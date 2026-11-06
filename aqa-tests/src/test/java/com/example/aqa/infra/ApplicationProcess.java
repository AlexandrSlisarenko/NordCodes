package com.example.aqa.infra;

import com.example.aqa.config.TestConfig;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class ApplicationProcess {

    private Process process;

    public void start() {
        Path jar = Path.of(TestConfig.appJar()).toAbsolutePath();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Не найден jar приложения: " + jar
                    + ". Положите файл в папку app/ или укажите путь через -Dapp.jar=...");
        }
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        File log = Path.of("target", "app.log").toAbsolutePath().toFile();
        log.getParentFile().mkdirs();

        ProcessBuilder builder = new ProcessBuilder(
                java,
                "-Dsecret=" + TestConfig.apiKey(),
                "-Dmock=" + TestConfig.mockUrl(),
                "-jar", jar.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log);
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось запустить приложение", e);
        }
        waitUntilReady(log);
    }

    private void waitUntilReady(File log) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest probe = HttpRequest.newBuilder(URI.create(TestConfig.appUrl() + "/endpoint"))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        long deadline = System.nanoTime() + Duration.ofSeconds(TestConfig.startupTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Приложение завершилось при старте, см. лог: " + log);
            }
            try {
                client.send(probe, HttpResponse.BodyHandlers.discarding());
                return;
            } catch (IOException e) {
                sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Ожидание старта приложения прервано", e);
            }
        }
        stop();
        throw new IllegalStateException("Приложение не запустилось за "
                + TestConfig.startupTimeoutSeconds() + " с, см. лог: " + log);
    }

    public void stop() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        process = null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
