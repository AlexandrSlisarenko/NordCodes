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
import org.junit.jupiter.params.provider.ValueSource;

@Epic(Labels.EPIC)
@Feature(Labels.F_DO_ACTION)
@DisplayName("ACTION: действие вошедшего пользователя")
class ActionTest extends BaseApiTest {

    @Test
    @Tag("smoke")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Вошедший пользователь успешно выполняет действие")
    @Description("Сервис обращается к внешнему /doAction ровно один раз и отвечает {\"result\":\"OK\"}.")
    void loggedInUserCanPerformAction() {
        String token = loggedInUser();

        ApiResponse response = api.action(token);

        Checks.isOk(response);
        Checks.isJson(response);
        Checks.doActionCalls(mock, token, 1);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Запрос к внешнему сервису /doAction оформлен по контракту")
    void doActionRequestMatchesContract() {
        String token = loggedInUser();

        api.action(token);

        Checks.externalRequestFormat(mock.doActionRequests(token), token);
    }

    @Test
    @Tag("smoke")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Без предварительного входа действие запрещено (403)")
    @Description("Токен, который не проходил LOGIN, не может выполнять ACTION. "
            + "Внешний сервис при этом не вызывается.")
    void actionWithoutLoginIsForbidden() {
        String token = newTokenWithHealthyExternalService();

        ApiResponse response = api.action(token);

        Checks.isErrorContaining(response, 403, "not found");
        Checks.noExternalCalls(mock, token);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("После выхода действие снова запрещено (403)")
    void actionAfterLogoutIsForbidden() {
        String token = loggedInUser();
        Checks.isOk(api.logout(token));

        Checks.isErrorContaining(api.action(token), 403, "not found");
        Checks.doActionCalls(mock, token, 0);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Действие можно выполнять многократно в рамках одной сессии")
    void actionCanBeRepeated() {
        String token = loggedInUser();

        Checks.isOk(api.action(token));
        Checks.isOk(api.action(token));
        Checks.isOk(api.action(token));

        Checks.doActionCalls(mock, token, 3);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Действие не вызывает повторную аутентификацию во внешнем сервисе")
    void actionDoesNotCallAuth() {
        String token = loggedInUser();

        api.action(token);

        Checks.authCalls(mock, token, 1);
    }

    @ParameterizedTest(name = "внешний сервис ответил {0}")
    @ValueSource(ints = {200, 201, 204})
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Любой успешный код (2xx) внешнего сервиса означает успешное действие")
    void anySuccessCodeMeansActionDone(int externalStatus) {
        String token = loggedInUser();
        mock.stubDoAction(token, externalStatus);

        Checks.isOk(api.action(token));
    }

    @ParameterizedTest(name = "внешний сервис ответил {0}")
    @ValueSource(ints = {400, 401, 403, 404, 500, 502, 503})
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Ошибка внешнего сервиса (4xx/5xx): действие не выполнено, но сессия сохраняется")
    @Description("Сбой во внешнем сервисе при ACTION — ошибка для пользователя, но не выход из системы: "
            + "когда сервис восстановится, тот же токен сможет выполнить действие без повторного входа.")
    void externalErrorFailsActionButKeepsSession(int externalStatus) {
        String token = loggedInUser();
        mock.stubDoAction(token, externalStatus);

        Checks.isFailure(api.action(token));

        mock.stubDoAction(token, 200);
        Checks.isOk(api.action(token));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Пользователь не может выполнить действие чужим токеном")
    void cannotActUnderAnotherUsersToken() {
        loggedInUser();
        String stranger = newTokenWithHealthyExternalService();

        Checks.isErrorContaining(api.action(stranger), 403, "not found");
    }
}
