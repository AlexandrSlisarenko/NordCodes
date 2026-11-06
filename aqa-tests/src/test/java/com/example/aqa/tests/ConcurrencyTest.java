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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Epic(Labels.EPIC)
@Feature(Labels.F_CONCURRENCY)
@Tag("concurrency")
@DisplayName("Одновременные запросы (гонки, общее хранилище токенов)")
class ConcurrencyTest extends BaseApiTest {

    private static final int THREADS = 20;

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("20 одновременных LOGIN с одним токеном: успешен ровно один")
    @Description("Проверка гонки: токен не может быть сохранён дважды. Один запрос получает OK, "
            + "остальные — 409, а внешний сервис вызывается ровно один раз.")
    void onlyOneOfParallelLoginsWins() {
        String token = newTokenWithHealthyExternalService();

        List<ApiResponse> responses = inParallel(THREADS, () ->
                api.send(TestConfig.apiKey(), token, EndpointClient.LOGIN));

        Allure.step("Проверить: один ответ 200 и " + (THREADS - 1) + " ответов 409", () -> {
            assertThat(countWithStatus(responses, 200)).as("Ответов 200").isEqualTo(1);
            assertThat(countWithStatus(responses, 409)).as("Ответов 409").isEqualTo(THREADS - 1);
        });
        Checks.authCalls(mock, token, 1);
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("20 одновременных LOGIN с разными токенами: успешны все, и каждый может действовать")
    void parallelLoginsOfDifferentUsersDoNotInterfere() {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(newTokenWithHealthyExternalService());
        }

        List<ApiResponse> responses = inParallelOver(tokens, token ->
                api.send(TestConfig.apiKey(), token, EndpointClient.LOGIN));

        Allure.step("Проверить: все " + THREADS + " входов успешны", () -> {
            assertThat(countWithStatus(responses, 200)).as("Ответов 200").isEqualTo(THREADS);
        });
        List<ApiResponse> actions = inParallelOver(tokens, token ->
                api.send(TestConfig.apiKey(), token, EndpointClient.ACTION));
        Allure.step("Проверить: все " + THREADS + " пользователей выполнили действие", () -> {
            assertThat(countWithStatus(actions, 200)).as("Ответов 200").isEqualTo(THREADS);
        });
    }

    @Test
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("20 одновременных LOGOUT с одним токеном: успешен ровно один")
    void onlyOneOfParallelLogoutsWins() {
        String token = loggedInUser();

        List<ApiResponse> responses = inParallel(THREADS, () ->
                api.send(TestConfig.apiKey(), token, EndpointClient.LOGOUT));

        Allure.step("Проверить: один ответ 200 и " + (THREADS - 1) + " ответов 403", () -> {
            assertThat(countWithStatus(responses, 200)).as("Ответов 200").isEqualTo(1);
            assertThat(countWithStatus(responses, 403)).as("Ответов 403").isEqualTo(THREADS - 1);
        });
    }

    @Test
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("20 одновременных ACTION одного пользователя: все успешны")
    void parallelActionsOfOneUserAllSucceed() {
        String token = loggedInUser();

        List<ApiResponse> responses = inParallel(THREADS, () ->
                api.send(TestConfig.apiKey(), token, EndpointClient.ACTION));

        Allure.step("Проверить: все " + THREADS + " действий успешны", () -> {
            assertThat(countWithStatus(responses, 200)).as("Ответов 200").isEqualTo(THREADS);
        });
        Checks.doActionCalls(mock, token, THREADS);
    }

    private static long countWithStatus(List<ApiResponse> responses, int status) {
        return responses.stream().filter(r -> r.status() == status).count();
    }

    private interface TokenCall {
        ApiResponse call(String token);
    }

    private List<ApiResponse> inParallelOver(List<String> tokens, TokenCall call) {
        List<Callable<ApiResponse>> tasks = new ArrayList<>();
        for (String token : tokens) {
            tasks.add(() -> call.call(token));
        }
        return runTogether(tasks);
    }

    private List<ApiResponse> inParallel(int count, Callable<ApiResponse> call) {
        List<Callable<ApiResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(call);
        }
        return runTogether(tasks);
    }

    private List<ApiResponse> runTogether(List<Callable<ApiResponse>> tasks) {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<ApiResponse>> futures = new ArrayList<>();
            for (Callable<ApiResponse> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();
            List<ApiResponse> responses = new ArrayList<>();
            for (Future<ApiResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Тест прерван", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Параллельный запрос не завершился корректно", e);
        } finally {
            pool.shutdownNow();
        }
    }
}
