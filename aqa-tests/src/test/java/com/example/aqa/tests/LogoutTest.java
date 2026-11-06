package com.example.aqa.tests;

import com.example.aqa.client.ApiResponse;
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
@Feature(Labels.F_LOGOUT)
@DisplayName("LOGOUT: выход пользователя из системы")
class LogoutTest extends BaseApiTest {

    @Test
    @Tag("smoke")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Вошедший пользователь успешно выходит")
    @Description("Выход — внутренняя операция сервиса: он удаляет токен и НЕ обращается ко внешнему сервису.")
    void loggedInUserCanLogout() {
        String token = loggedInUser();

        ApiResponse response = api.logout(token);

        Checks.isOk(response);
        Checks.isJson(response);
        Checks.authCalls(mock, token, 1);     // только от входа
        Checks.doActionCalls(mock, token, 0);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Выход без предварительного входа невозможен (403)")
    void logoutWithoutLoginIsForbidden() {
        String token = newTokenWithHealthyExternalService();

        Checks.isErrorContaining(api.logout(token), 403, "not found");
        Checks.noExternalCalls(mock, token);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Повторный выход отклоняется (403): сессии уже нет")
    void secondLogoutIsRejected() {
        String token = loggedInUser();
        Checks.isOk(api.logout(token));

        Checks.isErrorContaining(api.logout(token), 403, "not found");
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Выход одного пользователя не затрагивает сессию другого")
    void logoutOfOneUserDoesNotAffectAnother() {
        String alice = loggedInUser();
        String bob = loggedInUser();

        Checks.isOk(api.logout(alice));

        Checks.isOk(api.action(bob));
        Checks.isErrorContaining(api.action(alice), 403, "not found");
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Выход работает, даже если внешний сервис недоступен")
    @Description("Так как при выходе внешний сервис не нужен, его сбой не должен мешать завершить сессию.")
    void logoutWorksWhenExternalServiceIsDown() {
        String token = loggedInUser();
        mock.stubAuth(token, 503);
        mock.stubDoAction(token, 503);

        Checks.isOk(api.logout(token));
    }
}
