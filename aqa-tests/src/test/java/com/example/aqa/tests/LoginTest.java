package com.example.aqa.tests;

import com.example.aqa.client.ApiResponse;
import com.example.aqa.client.Checks;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

@Epic(Labels.EPIC)
@Feature(Labels.F_LOGIN)
@DisplayName("LOGIN: вход пользователя в систему")
class LoginTest extends BaseApiTest {

    @Test
    @Tag("smoke")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Успешный вход: внешний сервис подтвердил токен")
    @Description("Сервис обращается к внешнему /auth ровно один раз и отвечает {\"result\":\"OK\"}. "
            + "После этого токен считается вошедшим в систему.")
    void successfulLogin() {
        String token = newTokenWithHealthyExternalService();

        ApiResponse response = api.login(token);

        Checks.isOk(response);
        Checks.isJson(response);
        Checks.authCalls(mock, token, 1);
        Checks.doActionCalls(mock, token, 0);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Запрос к внешнему сервису /auth оформлен по контракту")
    @Description("Внешний сервис ожидает POST, Content-Type: application/x-www-form-urlencoded, "
            + "Accept: application/json и тело «token=<токен>».")
    void authRequestMatchesContract() {
        String token = newTokenWithHealthyExternalService();

        api.login(token);

        Checks.externalRequestFormat(mock.authRequests(token), token);
    }

    @Test
    @Tag("smoke")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("После успешного входа токен может выполнять действия")
    void loginEnablesActions() {
        String token = newTokenWithHealthyExternalService();

        Checks.isOk(api.login(token));

        Checks.isOk(api.action(token));
    }

    @ParameterizedTest(name = "внешний сервис ответил {0}")
    @ValueSource(ints = {200, 201, 204})
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Любой успешный код (2xx) внешнего сервиса означает успешный вход")
    void anySuccessCodeMeansLoggedIn(int externalStatus) {
        String token = newToken();
        mock.stubAuth(token, externalStatus);

        Checks.isOk(api.login(token));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("irrelevantBodies")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Тело ответа внешнего сервиса не важно — смотрится только код")
    @Description("По условию приложение анализирует только код ответа. "
            + "Даже если в теле написано «ERROR» или пришла HTML-страница, при коде 200 вход считается успешным.")
    void externalBodyIsIgnored(String caseName, String body) {
        String token = newToken();
        mock.stubAuth(token, aResponse().withStatus(200).withBody(body));

        Checks.isOk(api.login(token));
    }

    static Stream<Arguments> irrelevantBodies() {
        return Stream.of(
                Arguments.of("Пустое тело", ""),
                Arguments.of("JSON с result=ERROR", "{\"result\":\"ERROR\",\"message\":\"denied\"}"),
                Arguments.of("HTML-страница", "<html><body>Access denied</body></html>"),
                Arguments.of("Произвольный текст", "FAIL"));
    }

    @ParameterizedTest(name = "внешний сервис ответил {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500, 502, 503})
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Ошибка внешнего сервиса (4xx/5xx): вход не выполнен, токен не сохраняется")
    @Description("Если внешний сервис не подтвердил вход, пользователь не должен считаться вошедшим: "
            + "ответ — ошибка, а последующее действие с этим токеном запрещено.")
    void externalErrorMeansLoginFails(int externalStatus) {
        String token = newToken();
        mock.stubAuth(token, externalStatus);
        mock.stubDoAction(token, 200);

        Checks.isFailure(api.login(token));

        Checks.isErrorContaining(api.action(token), 403, "not found");
        Checks.doActionCalls(mock, token, 0);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Повторный вход уже вошедшего токена отклоняется (409)")
    @Description("Токен, который уже в системе, нельзя «залогинить» второй раз. "
            + "Внешний сервис при повторной попытке не вызывается.")
    void duplicateLoginIsRejected() {
        String token = loggedInUser();

        ApiResponse second = api.login(token);

        Checks.isErrorContaining(second, 409, "already exists");
        Checks.authCalls(mock, token, 1);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Повторный вход не ломает действующую сессию")
    void duplicateLoginKeepsSessionAlive() {
        String token = loggedInUser();

        api.login(token);

        Checks.isOk(api.action(token));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("После выхода тот же токен может войти снова")
    void loginAfterLogoutIsAllowed() {
        String token = loggedInUser();
        Checks.isOk(api.logout(token));

        Checks.isOk(api.login(token));

        Checks.isOk(api.action(token));
        Checks.authCalls(mock, token, 2);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("После неудачного входа можно повторить попытку, когда внешний сервис восстановился")
    void loginCanBeRetriedAfterFailure() {
        String token = newTokenWithHealthyExternalService();
        mock.stubAuth(token, 503);
        Checks.isFailure(api.login(token));

        mock.stubAuth(token, 200);
        Checks.isOk(api.login(token));

        Checks.isOk(api.action(token));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Вход одного пользователя не даёт прав другому")
    void loginOfOneUserDoesNotAuthorizeAnother() {
        String alice = loggedInUser();
        String bob = newTokenWithHealthyExternalService();

        Checks.isErrorContaining(api.action(bob), 403, "not found");
        Checks.isOk(api.action(alice));
    }
}
