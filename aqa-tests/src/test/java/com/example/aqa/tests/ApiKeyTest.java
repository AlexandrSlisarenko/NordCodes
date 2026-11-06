package com.example.aqa.tests;

import com.example.aqa.client.Checks;
import com.example.aqa.client.EndpointClient;
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

import com.example.aqa.client.ApiResponse;

@Epic(Labels.EPIC)
@Feature(Labels.F_SECURITY)
@DisplayName("Защита API-ключом (заголовок X-Api-Key)")
class ApiKeyTest extends BaseApiTest {

    @Test
    @Tag("smoke")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Запрос без заголовка X-Api-Key отклоняется")
    @Description("Без ключа сервис не должен выполнять никакие действия и обязан ответить 401.")
    void requestWithoutKeyIsRejected() {
        String token = newTokenWithHealthyExternalService();

        ApiResponse response = api.send(null, token, EndpointClient.LOGIN);

        Checks.isErrorContaining(response, 401, "API Key");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrongKeys")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Запрос с неверным ключом отклоняется")
    @Description("Ключ сравнивается строго: регистр, лишние и недостающие символы, пустое значение — всё это ошибка 401.")
    void requestWithWrongKeyIsRejected(String caseName, String wrongKey) {
        String token = newTokenWithHealthyExternalService();

        ApiResponse response = api.send(wrongKey, token, EndpointClient.LOGIN);

        Checks.isErrorContaining(response, 401, "API Key");
    }

    static Stream<Arguments> wrongKeys() {
        return Stream.of(
                Arguments.of("Произвольная строка", "wrong"),
                Arguments.of("Верный ключ в другом регистре (строчные)", "qazwsxedc"),
                Arguments.of("Верный ключ в другом регистре (заглавные)", "QAZWSXEDC"),
                Arguments.of("Ключ без последнего символа", "qazWSXed"),
                Arguments.of("Ключ с лишним символом в конце", "qazWSXedcX"),
                Arguments.of("Пустой ключ", ""));
    }

    @ParameterizedTest(name = "action={0}")
    @ValueSource(strings = {"LOGIN", "ACTION", "LOGOUT"})
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Ни одно из трёх действий не работает без ключа")
    @Description("Защита должна действовать на все действия, а не только на вход. "
            + "Внешний сервис при этом не должен получать ни одного запроса.")
    void everyActionRequiresKey(String action) {
        String token = newTokenWithHealthyExternalService();

        ApiResponse response = api.send(null, token, action);

        Checks.isErrorContaining(response, 401, "API Key");
        Checks.noExternalCalls(mock, token);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Проверка ключа выполняется раньше проверки остальных параметров")
    @Description("Если ключ неверный, сервис не должен подсказывать постороннему, что именно не так с токеном и action: "
            + "ответ должен быть 401, а не 400.")
    void keyIsCheckedBeforeParameters() {
        ApiResponse response = api.send("wrong", "not-a-token", "NOT-AN-ACTION");

        Checks.isErrorContaining(response, 401, "API Key");
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Вход с неверным ключом не создаёт сессию")
    @Description("Отклонённый запрос LOGIN не должен ни сохранять токен, ни обращаться к внешнему сервису.")
    void loginWithWrongKeyDoesNotCreateSession() {
        String token = newTokenWithHealthyExternalService();

        api.send("wrong", token, EndpointClient.LOGIN);

        Checks.noExternalCalls(mock, token);
        Checks.isErrorContaining(api.action(token), 403, "not found");
    }
}
