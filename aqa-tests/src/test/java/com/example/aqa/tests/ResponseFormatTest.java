package com.example.aqa.tests;

import com.example.aqa.client.ApiResponse;
import com.example.aqa.client.Checks;
import com.example.aqa.client.EndpointClient;
import com.example.aqa.config.TestConfig;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Epic(Labels.EPIC)
@Feature(Labels.F_CONTRACT)
@DisplayName("Формат ответов: всегда JSON, ошибки содержат причину")
class ResponseFormatTest extends BaseApiTest {

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Ответ на любую ошибку — JSON с result=ERROR и текстом причины")
    @Description("Проверяем по одному примеру каждого вида ошибки: 400 (плохие данные), 401 (нет ключа), "
            + "403 (нет сессии), 409 (повторный вход), 5xx (сбой внешнего сервиса).")
    void everyKindOfErrorHasJsonBodyWithReason() {
        String loggedIn = loggedInUser();
        String failingExternal = newToken();
        mock.stubAuth(failingExternal, 500);

        Allure.step("400: токен неверного формата", () -> {
            ApiResponse response = api.send(TestConfig.apiKey(), "BAD", EndpointClient.LOGIN);
            Checks.isJson(response);
            Checks.isError(response, 400);
        });
        Allure.step("401: нет API-ключа", () -> {
            ApiResponse response = api.send(null, loggedIn, EndpointClient.ACTION);
            Checks.isJson(response);
            Checks.isError(response, 401);
        });
        Allure.step("403: токен не найден", () -> {
            ApiResponse response = api.action(newToken());
            Checks.isJson(response);
            Checks.isError(response, 403);
        });
        Allure.step("409: повторный вход", () -> {
            ApiResponse response = api.login(loggedIn);
            Checks.isJson(response);
            Checks.isError(response, 409);
        });
        Allure.step("5xx: внешний сервис вернул ошибку", () -> {
            ApiResponse response = api.login(failingExternal);
            Checks.isJson(response);
            Checks.isServerError(response);
        });
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Сообщение об ошибке не раскрывает внутренние детали (стек-трейсы, имена классов)")
    @Description("При сбое внешнего сервиса пользователь должен получить короткое безопасное сообщение, "
            + "а не детали реализации.")
    void errorMessageDoesNotLeakInternals() {
        String token = newToken();
        mock.stubAuth(token, 500);

        ApiResponse response = api.login(token);

        Allure.step("Проверить: в сообщении нет упоминаний исключений, пакетов и адресов внешнего сервиса", () -> {
            org.assertj.core.api.Assertions.assertThat(response.body())
                    .as("Тело ответа")
                    .doesNotContain("Exception", "org.springframework", "localhost:", "at java.");
        });
    }
}
