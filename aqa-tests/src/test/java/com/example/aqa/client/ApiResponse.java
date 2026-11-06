package com.example.aqa.client;

import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

public record ApiResponse(int status, String contentType, String body, String result, String message, long timeMs) {

    static ApiResponse from(Response response) {
        String body = response.asString();
        return new ApiResponse(
                response.statusCode(),
                response.contentType(),
                body,
                field(body, "result"),
                field(body, "message"),
                response.time());
    }

    private static String field(String body, String name) {
        try {
            return JsonPath.from(body).getString(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "HTTP " + status + " " + body;
    }
}
