package com.example.aqa.tests;

import com.example.aqa.client.ApiResponse;
import com.example.aqa.client.Checks;
import com.github.tomakehurst.wiremock.http.Fault;
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

import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

@Epic(Labels.EPIC)
@Feature(Labels.F_EXTERNAL)
@DisplayName("Устойчивость к сбоям внешнего сервиса (обрыв связи, мусор в ответе, зависание)")
class ExternalServiceFailuresTest extends BaseApiTest {

    private static final long MAX_REASONABLE_WAIT_MS = 20_000;

    static Stream<Arguments> networkFaults() {
        return Stream.of(
                Arguments.of("Соединение сброшено внешним сервисом", Fault.CONNECTION_RESET_BY_PEER),
                Arguments.of("Внешний сервис закрыл соединение, не ответив", Fault.EMPTY_RESPONSE),
                Arguments.of("Внешний сервис прислал случайные байты", Fault.RANDOM_DATA_THEN_CLOSE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkFaults")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("LOGIN при сетевом сбое: ошибка сервера, токен не сохраняется")
    @Description("Если ответ внешнего сервиса получить не удалось, сервис не должен считать вход успешным "
            + "и не должен «зависать»: пользователь получает ошибку 5xx в формате JSON.")
    void loginSurvivesNetworkFault(String caseName, Fault fault) {
        String token = newToken();
        mock.stubAuthFault(token, fault);
        mock.stubDoAction(token, 200);

        Checks.isServerError(api.login(token));

        Checks.isErrorContaining(api.action(token), 403, "not found");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("networkFaults")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("ACTION при сетевом сбое: ошибка сервера, сессия сохраняется")
    void actionSurvivesNetworkFault(String caseName, Fault fault) {
        String token = loggedInUser();
        mock.stubDoActionFault(token, fault);

        Checks.isServerError(api.action(token));

        mock.stubDoAction(token, 200);
        Checks.isOk(api.action(token));
    }

    @Test
    @Tag("slow")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("LOGIN, когда внешний сервис «завис»: сервис отвечает ошибкой в разумное время")
    @Description("Внешний сервис отвечает через 25 секунд. Приложение не должно ждать бесконечно: "
            + "ожидается ошибка 5xx не позднее чем через 20 секунд, токен не сохраняется.")
    void loginDoesNotHangWhenExternalServiceStalls() {
        String token = newToken();
        mock.stubAuth(token, aResponse().withStatus(200).withFixedDelay(25_000));

        ApiResponse response = api.login(token);

        Checks.isServerError(response);
        assertThat(response.timeMs()).as("Время ожидания ответа, мс").isLessThan(MAX_REASONABLE_WAIT_MS);
        Checks.isErrorContaining(api.action(token), 403, "not found");
    }

    @Test
    @Tag("slow")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("ACTION, когда внешний сервис «завис»: сервис отвечает ошибкой в разумное время, сессия сохраняется")
    void actionDoesNotHangWhenExternalServiceStalls() {
        String token = loggedInUser();
        mock.stubDoAction(token, aResponse().withStatus(200).withFixedDelay(25_000));

        ApiResponse response = api.action(token);

        Checks.isServerError(response);
        assertThat(response.timeMs()).as("Время ожидания ответа, мс").isLessThan(MAX_REASONABLE_WAIT_MS);

        mock.stubDoAction(token, 200);
        Checks.isOk(api.action(token));
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Медленный, но укладывающийся в лимит внешний сервис (2 секунды): вход успешен")
    @Description("Задержка в пределах таймаута не должна приводить к ошибке.")
    void slowButAcceptableExternalServiceStillWorks() {
        String token = newToken();
        mock.stubAuth(token, aResponse().withStatus(200).withFixedDelay(2_000));

        Checks.isOk(api.login(token));
    }
}
