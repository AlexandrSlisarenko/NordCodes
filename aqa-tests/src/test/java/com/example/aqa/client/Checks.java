package com.example.aqa.client;

import com.example.aqa.infra.ExternalServiceMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.qameta.allure.Allure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Checks {

    public static void isOk(ApiResponse response) {
        Allure.step("Проверить: сервис ответил успехом (HTTP 200, {\"result\":\"OK\"})", () -> {
            assertThat(response.status()).as("HTTP-статус ответа. Ответ: %s", response).isEqualTo(200);
            assertThat(response.result()).as("Поле result. Ответ: %s", response).isEqualTo("OK");
            assertThat(response.body().replaceAll("\\s", ""))
                    .as("Тело успешного ответа должно содержать только поле result").isEqualTo("{\"result\":\"OK\"}");
        });
    }

    public static void isError(ApiResponse response, int expectedStatus) {
        Allure.step("Проверить: сервис ответил ошибкой (HTTP " + expectedStatus + ", result=ERROR, есть message)", () -> {
            assertThat(response.status()).as("HTTP-статус ответа. Ответ: %s", response).isEqualTo(expectedStatus);
            assertErrorBody(response);
        });
    }

    public static void isErrorContaining(ApiResponse response, int expectedStatus, String textInMessage) {
        Allure.step("Проверить: HTTP " + expectedStatus + ", result=ERROR, в message есть «" + textInMessage + "»", () -> {
            assertThat(response.status()).as("HTTP-статус ответа. Ответ: %s", response).isEqualTo(expectedStatus);
            assertErrorBody(response);
            assertThat(response.message()).as("Текст сообщения об ошибке").contains(textInMessage);
        });
    }

    public static void isServerError(ApiResponse response) {
        Allure.step("Проверить: сервис сообщил о внутренней ошибке (HTTP 5xx, result=ERROR)", () -> {
            assertThat(response.status()).as("HTTP-статус ответа. Ответ: %s", response).isBetween(500, 599);
            assertErrorBody(response);
        });
    }

    public static void isFailure(ApiResponse response) {
        Allure.step("Проверить: сервис не считает операцию успешной (HTTP 4xx/5xx, result=ERROR)", () -> {
            assertThat(response.status()).as("HTTP-статус ответа. Ответ: %s", response).isBetween(400, 599);
            assertErrorBody(response);
        });
    }

    public static void isClientError(ApiResponse response, int... allowedStatuses) {
        Allure.step("Проверить: сервис вернул корректный код клиентской ошибки", () -> {
            assertThat(response.status())
                    .as("Ошибка клиента не должна превращаться в 5xx. Ответ: %s", response)
                    .isIn(java.util.Arrays.stream(allowedStatuses).boxed().toArray());
        });
    }

    public static void isJson(ApiResponse response) {
        Allure.step("Проверить: ответ имеет формат JSON (Content-Type: application/json)", () -> {
            assertThat(response.contentType()).as("Content-Type ответа").containsIgnoringCase("application/json");
        });
    }

    private static void assertErrorBody(ApiResponse response) {
        assertThat(response.result()).as("Поле result. Ответ: %s", response).isEqualTo("ERROR");
        assertThat(response.message()).as("Поле message с причиной ошибки. Ответ: %s", response).isNotBlank();
    }

    public static void authCalls(ExternalServiceMock mock, String token, int expected) {
        Allure.step("Проверить: внешний сервис /auth получил запросов по этому токену: " + expected, () -> {
            assertThat(mock.authRequests(token)).as("Число запросов к /auth").hasSize(expected);
        });
    }

    public static void doActionCalls(ExternalServiceMock mock, String token, int expected) {
        Allure.step("Проверить: внешний сервис /doAction получил запросов по этому токену: " + expected, () -> {
            assertThat(mock.doActionRequests(token)).as("Число запросов к /doAction").hasSize(expected);
        });
    }

    public static void noExternalCalls(ExternalServiceMock mock, String token) {
        Allure.step("Проверить: приложение вообще не обращалось к внешнему сервису по этому токену", () -> {
            assertThat(mock.authRequests(token)).as("Запросы к /auth").isEmpty();
            assertThat(mock.doActionRequests(token)).as("Запросы к /doAction").isEmpty();
        });
    }

    public static void externalRequestFormat(List<LoggedRequest> requests, String token) {
        Allure.step("Проверить: запрос к внешнему сервису оформлен по контракту (POST, form-urlencoded, token=...)", () -> {
            assertThat(requests).as("Запросы к внешнему сервису").isNotEmpty();
            for (LoggedRequest request : requests) {
                assertThat(String.valueOf(request.getMethod())).as("HTTP-метод").isEqualTo("POST");
                assertThat(request.getHeader("Content-Type")).as("Заголовок Content-Type")
                        .startsWith("application/x-www-form-urlencoded");
                assertThat(request.getHeader("Accept")).as("Заголовок Accept").contains("application/json");
                assertThat(request.getBodyAsString()).as("Тело запроса").isEqualTo(ExternalServiceMock.formBody(token));
            }
        });
    }
}
