package com.example.aqa.tests;

import com.example.aqa.client.Checks;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Epic(Labels.EPIC)
@Feature(Labels.F_SCENARIO)
@DisplayName("Сквозные пользовательские сценарии")
class SessionScenarioTest extends BaseApiTest {

    @Test
    @Tag("smoke")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Полный жизненный цикл: вход → действия → выход → доступ закрыт → повторный вход")
    @Description("Главный сценарий пользователя от начала до конца, включая повторное использование токена после выхода.")
    void fullUserLifecycle() {
        String token = newTokenWithHealthyExternalService();

        Checks.isErrorContaining(api.action(token), 403, "not found");   // до входа действовать нельзя
        Checks.isOk(api.login(token));                                    // вход
        Checks.isOk(api.action(token));                                   // действия
        Checks.isOk(api.action(token));
        Checks.isOk(api.logout(token));                                   // выход
        Checks.isErrorContaining(api.action(token), 403, "not found");   // после выхода действовать нельзя
        Checks.isOk(api.login(token));                                    // тот же токен входит снова
        Checks.isOk(api.action(token));
        Checks.isOk(api.logout(token));

        Checks.authCalls(mock, token, 2);
        Checks.doActionCalls(mock, token, 3);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Два пользователя работают независимо друг от друга")
    @Description("Вход, действия и выход одного пользователя не влияют на другого.")
    void twoUsersAreIndependent() {
        String alice = newTokenWithHealthyExternalService();
        String bob = newTokenWithHealthyExternalService();

        Checks.isOk(api.login(alice));
        Checks.isErrorContaining(api.action(bob), 403, "not found");   // Боб ещё не входил
        Checks.isOk(api.login(bob));
        Checks.isOk(api.action(alice));
        Checks.isOk(api.logout(alice));
        Checks.isOk(api.action(bob));                                    // Боб по-прежнему в системе
        Checks.isErrorContaining(api.action(alice), 403, "not found");

        Checks.doActionCalls(mock, alice, 1);
        Checks.doActionCalls(mock, bob, 1);
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Сбой внешнего сервиса посреди сессии: после восстановления работа продолжается без повторного входа")
    void sessionSurvivesTemporaryExternalOutage() {
        String token = loggedInUser();

        mock.stubDoAction(token, 503);
        Checks.isFailure(api.action(token));
        mock.stubDoAction(token, 200);

        Checks.isOk(api.action(token));
        Checks.authCalls(mock, token, 1);
    }
}
