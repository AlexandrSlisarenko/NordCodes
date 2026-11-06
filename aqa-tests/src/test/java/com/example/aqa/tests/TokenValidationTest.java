package com.example.aqa.tests;

import com.example.aqa.client.ApiResponse;
import com.example.aqa.client.Checks;
import com.example.aqa.client.EndpointClient;
import com.example.aqa.config.TestConfig;
import com.example.aqa.data.TokenFactory;
import com.example.aqa.infra.ExternalServiceMock;
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

import static org.assertj.core.api.Assertions.assertThat;

@Epic(Labels.EPIC)
@Feature(Labels.F_TOKEN)
@DisplayName("Проверка формата токена (32 символа A-Z0-9)")
class TokenValidationTest extends BaseApiTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("validTokens")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Корректный токен принимается")
    @Description("Граничные значения допустимого набора: только цифры, только буквы A-F, смесь, нижняя и верхняя границы.")
    void validTokenIsAccepted(String caseName, String token) {
        track(token);
        mock.stubAuth(token, 200);

        ApiResponse response = api.send(TestConfig.apiKey(), token, EndpointClient.LOGIN);

        Checks.isOk(response);
    }

    static Stream<Arguments> validTokens() {
        return Stream.of(
                Arguments.of("Только цифры 0", "0".repeat(32)),
                Arguments.of("Только цифры 9", "9".repeat(32)),
                Arguments.of("Только буква A", "A".repeat(32)),
                Arguments.of("Только буква F", "F".repeat(32)),
                Arguments.of("Все допустимые символы 0-9 и A-F", "0123456789ABCDEF0123456789ABCDEF"),
                Arguments.of("Случайный токен", TokenFactory.random()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Некорректный токен отклоняется с кодом 400")
    @Description("Токен должен состоять ровно из 32 допустимых символов. Всё остальное — ошибка 400 "
            + "с понятным сообщением, а не сбой сервера.")
    void invalidTokenIsRejected(String caseName, String token) {
        ApiResponse response = api.send(TestConfig.apiKey(), token, EndpointClient.LOGIN);

        Checks.isErrorContaining(response, 400, "token");
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("Пустой токен", ""),
                Arguments.of("Слишком короткий: 1 символ", "A"),
                Arguments.of("Слишком короткий: 31 символ", "A".repeat(31)),
                Arguments.of("Слишком длинный: 33 символа", "A".repeat(33)),
                Arguments.of("Слишком длинный: 64 символа", "A".repeat(64)),
                Arguments.of("Строчные буквы вместо заглавных", "a".repeat(32)),
                Arguments.of("Токен, начинающийся с пробела", " " + "A".repeat(31)),
                Arguments.of("Токен с пробелом в конце", "A".repeat(31) + " "),
                Arguments.of("Токен с переводом строки в конце", "A".repeat(31) + "\n"),
                Arguments.of("Пробел внутри токена", "A".repeat(15) + " " + "A".repeat(16)),
                Arguments.of("Спецсимволы", "!@#$%^&*".repeat(4)),
                Arguments.of("Дефис (UUID с дефисами)", "12345678-1234-1234-1234-123456789012"),
                Arguments.of("Кириллица, похожая на латиницу", "А".repeat(32)),
                Arguments.of("SQL-инъекция", "' OR '1'='1' --".repeat(3)));
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Запрос без параметра token отклоняется с кодом 400")
    void missingTokenIsRejected() {
        ApiResponse response = api.send(TestConfig.apiKey(), null, EndpointClient.LOGIN);

        Checks.isErrorContaining(response, 400, "token");
    }

    @ParameterizedTest(name = "action={0}")
    @ValueSource(strings = {"LOGIN", "ACTION", "LOGOUT"})
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Токен проверяется для всех трёх действий")
    void tokenIsValidatedForEveryAction(String action) {
        ApiResponse response = api.send(TestConfig.apiKey(), "SHORT", action);

        Checks.isErrorContaining(response, 400, "token");
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Некорректный токен не доходит до внешнего сервиса")
    @Description("Запрос с невалидным токеном должен быть остановлен самим приложением, "
            + "а не передан дальше во внешний сервис.")
    void invalidTokenDoesNotReachExternalService() {
        String shortToken = TokenFactory.random().substring(1); // 31 символ

        ApiResponse response = api.send(TestConfig.apiKey(), shortToken, EndpointClient.LOGIN);

        Checks.isError(response, 400);
        assertThat(mock.requestsContaining(ExternalServiceMock.AUTH, shortToken))
                .as("Запросы к /auth с этим токеном").isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tokensWithLettersAfterF")
    @Tag("defect")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("ДЕФЕКТ BUG-01: токен с буквами G-Z отклоняется, хотя по документации разрешён весь диапазон A-Z")
    @Description("ИЗВЕСТНЫЙ ДЕФЕКТ BUG-01. В документации токен — «строка длиной 32 символа, состоящая только из символов A-Z0-9». "
            + "Фактически приложение принимает только 0-9 и A-F (проверка ^[0-9A-F]{32}$), "
            + "поэтому корректные по документации токены получают ошибку 400.")
    void tokenWithLettersGToZIsAccepted(String caseName, String token) {
        track(token);
        mock.stubAuth(token, 200);

        ApiResponse response = api.send(TestConfig.apiKey(), token, EndpointClient.LOGIN);

        Checks.isOk(response);
    }

    static Stream<Arguments> tokensWithLettersAfterF() {
        return Stream.of(
                Arguments.of("Только буква Z", "Z".repeat(32)),
                Arguments.of("Буквы G-Z и цифры", "GHIJKLMNOPQRSTUVWXYZ012345678901"),
                Arguments.of("Цифры, A-F и G-V", "0123456789ABCDEFGHIJKLMNOPQRSTUV"));
    }
}
