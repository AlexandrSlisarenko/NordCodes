package com.example.aqa.tests;

import com.example.aqa.client.Checks;
import com.example.aqa.client.EndpointClient;
import com.example.aqa.data.TokenFactory;
import com.example.aqa.infra.ExternalServiceMock;
import com.example.aqa.infra.TestEnvironment;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseApiTest {

    protected final EndpointClient api = new EndpointClient();
    protected final ExternalServiceMock mock = TestEnvironment.mock();

    private final List<String> usedTokens = new CopyOnWriteArrayList<>();

    protected String newToken() {
        return track(TokenFactory.random());
    }

    protected String track(String token) {
        usedTokens.add(token);
        return token;
    }

    protected String newTokenWithHealthyExternalService() {
        String token = newToken();
        Allure.step("Подготовка: внешний сервис отвечает кодом 200 на /auth и /doAction для токена " + token, () -> {
            mock.stubAuth(token, 200);
            mock.stubDoAction(token, 200);
        });
        return token;
    }

    protected String loggedInUser() {
        String token = newTokenWithHealthyExternalService();
        Allure.step("Подготовка: пользователь входит в систему", () -> {
            Checks.isOk(api.login(token));
        });
        return token;
    }

    @AfterEach
    void releaseTokens() {
        usedTokens.forEach(api::logoutQuietly);
    }
}
