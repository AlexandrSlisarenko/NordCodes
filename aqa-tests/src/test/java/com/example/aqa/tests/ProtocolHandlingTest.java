package com.example.aqa.tests;

import com.example.aqa.client.ApiResponse;
import com.example.aqa.client.Checks;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

@Epic(Labels.EPIC)
@Feature(Labels.F_PROTOCOL)
@Tag("defect")
@DisplayName("Ошибки клиента не должны превращаться в «500 Internal Server Error»")
class ProtocolHandlingTest extends BaseApiTest {

    @ParameterizedTest(name = "метод {0}")
    @EnumSource(value = Method.class, names = {"GET", "PUT", "DELETE", "PATCH"})
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("ДЕФЕКТ BUG-02: неподдерживаемый HTTP-метод должен давать 405, а не 500")
    @Description("ИЗВЕСТНЫЙ ДЕФЕКТ BUG-02. Эндпоинт принимает только POST. На GET/PUT/DELETE/PATCH сервис отвечает 500, "
            + "хотя это ошибка клиента и ожидается 405 Method Not Allowed.")
    void unsupportedMethodGivesClientError(Method method) {
        ApiResponse response = api.sendCustom(method, "/endpoint", null, null, null);

        Checks.isClientError(response, 404, 405);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("ДЕФЕКТ BUG-02: несуществующий путь должен давать 404, а не 500")
    @Description("ИЗВЕСТНЫЙ ДЕФЕКТ BUG-02. Запрос на /other (такого эндпоинта нет) завершается кодом 500 вместо 404.")
    void unknownPathGivesNotFound() {
        ApiResponse response = api.sendCustom(Method.POST, "/other", ContentType.URLENC, "token=A&action=LOGIN", null);

        Checks.isClientError(response, 404);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("ДЕФЕКТ BUG-02: тело в формате JSON вместо form-urlencoded должно давать 400/415, а не 500")
    @Description("ИЗВЕСТНЫЙ ДЕФЕКТ BUG-02. Эндпоинт принимает только application/x-www-form-urlencoded. "
            + "Запрос с JSON-телом — ошибка клиента (400 или 415), но сервис отвечает 500.")
    void jsonBodyGivesClientError() {
        String body = "{\"token\":\"" + newToken() + "\",\"action\":\"LOGIN\"}";

        ApiResponse response = api.sendCustom(Method.POST, "/endpoint", ContentType.JSON, body, null);

        Checks.isClientError(response, 400, 415);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("ДЕФЕКТ BUG-02: параметры только в строке запроса (без тела) должны давать 400, а не 500")
    @Description("ИЗВЕСТНЫЙ ДЕФЕКТ BUG-02. Если token и action переданы в URL, а не в теле, "
            + "сервис должен либо обработать их, либо вернуть 400. Фактически возвращается 500.")
    void parametersInQueryStringGiveClientError() {
        String token = newTokenWithHealthyExternalService();

        ApiResponse response = api.sendCustom(Method.POST, "/endpoint", null, null,
                Map.of("token", token, "action", "LOGIN"));

        Checks.isClientError(response, 200, 400);
    }
}
