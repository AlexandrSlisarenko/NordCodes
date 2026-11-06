package com.example.aqa.tests;

import com.example.aqa.client.ApiResponse;
import com.example.aqa.client.Checks;
import com.example.aqa.config.TestConfig;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

@Epic(Labels.EPIC)
@Feature(Labels.F_ACTION)
@DisplayName("Проверка параметра action (допустимы только LOGIN, ACTION, LOGOUT)")
class ActionParameterTest extends BaseApiTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidActions")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Неизвестное действие отклоняется с кодом 400")
    @Description("Допустимы ровно три значения, регистр важен. Любое другое — ошибка 400, а не сбой сервера.")
    void unknownActionIsRejected(String caseName, String action) {
        String token = newTokenWithHealthyExternalService();

        ApiResponse response = api.send(TestConfig.apiKey(), token, action);

        Checks.isErrorContaining(response, 400, "action");
        Checks.noExternalCalls(mock, token);
    }

    static Stream<Arguments> invalidActions() {
        return Stream.of(
                Arguments.of("Пустое значение", ""),
                Arguments.of("Строчные буквы: login", "login"),
                Arguments.of("Смешанный регистр: Login", "Login"),
                Arguments.of("Строчные буквы: action", "action"),
                Arguments.of("Строчные буквы: logout", "logout"),
                Arguments.of("Несуществующее действие", "FOO"),
                Arguments.of("Два действия через запятую", "LOGIN,LOGOUT"),
                Arguments.of("Похожее слово: LOGINN", "LOGINN"),
                Arguments.of("Похожее слово: LOG", "LOG"),
                Arguments.of("SQL-инъекция", "LOGIN' OR '1'='1"));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Запрос без параметра action отклоняется с кодом 400")
    void missingActionIsRejected() {
        String token = newToken();

        ApiResponse response = api.send(TestConfig.apiKey(), token, null);

        Checks.isErrorContaining(response, 400, "action");
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Запрос без параметров отклоняется, и в ответе названы оба недостающих параметра")
    void requestWithoutParametersReportsBothProblems() {
        ApiResponse response = api.send(TestConfig.apiKey(), null, null);

        Checks.isErrorContaining(response, 400, "token");
        Checks.isErrorContaining(response, 400, "action");
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонённое неизвестное действие не портит существующую сессию")
    void rejectedActionKeepsExistingSession() {
        String token = loggedInUser();

        Checks.isError(api.send(TestConfig.apiKey(), token, "FOO"), 400);

        Checks.isOk(api.action(token));
    }
}
