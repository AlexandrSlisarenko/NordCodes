package com.example.aqa.client;

import com.example.aqa.config.TestConfig;
import io.qameta.allure.Allure;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

import static io.restassured.RestAssured.given;

public final class EndpointClient {

    public static final String ENDPOINT = "/endpoint";
    public static final String LOGIN = "LOGIN";
    public static final String ACTION = "ACTION";
    public static final String LOGOUT = "LOGOUT";

    // ---------- бизнес-операции ----------

    public ApiResponse login(String token) {
        return Allure.step("Пользователь входит в систему (LOGIN), токен " + token, () -> {
            return send(TestConfig.apiKey(), token, LOGIN);
        });
    }

    public ApiResponse action(String token) {
        return Allure.step("Пользователь выполняет действие (ACTION), токен " + token, () -> {
            return send(TestConfig.apiKey(), token, ACTION);
        });
    }

    public ApiResponse logout(String token) {
        return Allure.step("Пользователь выходит из системы (LOGOUT), токен " + token, () -> {
            return send(TestConfig.apiKey(), token, LOGOUT);
        });
    }

    public ApiResponse send(String apiKey, String token, String action) {
        RequestSpecification spec = baseSpec(true).contentType(ContentType.URLENC);
        if (apiKey != null) {
            spec.header("X-Api-Key", apiKey);
        }
        if (token != null) {
            spec.formParam("token", token);
        }
        if (action != null) {
            spec.formParam("action", action);
        }
        return ApiResponse.from(spec.post(ENDPOINT));
    }

    public ApiResponse sendCustom(Method method, String path, ContentType contentType,
                                  String body, Map<String, String> queryParams) {
        RequestSpecification spec = baseSpec(true).header("X-Api-Key", TestConfig.apiKey());
        if (contentType != null) {
            spec.contentType(contentType);
        }
        if (body != null) {
            spec.body(body);
        }
        if (queryParams != null) {
            spec.queryParams(queryParams);
        }
        return ApiResponse.from(spec.request(method, path));
    }

    public void logoutQuietly(String token) {
        try {
            baseSpec(false).contentType(ContentType.URLENC)
                    .header("X-Api-Key", TestConfig.apiKey())
                    .formParam("token", token)
                    .formParam("action", LOGOUT)
                    .post(ENDPOINT);
        } catch (RuntimeException ignored) {

        }
    }

    private RequestSpecification baseSpec(boolean withReport) {
        long timeoutMs = TestConfig.clientTimeoutSeconds() * 1000L;
        RestAssuredConfig config = RestAssuredConfig.config().httpClient(
                HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 5000)
                        .setParam("http.socket.timeout", (int) timeoutMs));
        RequestSpecification spec = given()
                .baseUri(TestConfig.appUrl())
                .config(config)
                .accept(ContentType.JSON);
        if (withReport) {
            spec.filter(new AllureRestAssured());
        }
        return spec;
    }
}
