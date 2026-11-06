package com.example.aqa.infra;

import com.example.aqa.config.TestConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class TestEnvironment {

    private static ExternalServiceMock mock;
    private static ApplicationProcess app;
    private static int users;

    public static synchronized void start() {
        users++;
        if (users > 1) {
            return;
        }
        mock = new ExternalServiceMock(TestConfig.mockPort());
        mock.start();
        if (TestConfig.startApp()) {
            app = new ApplicationProcess();
            try {
                app.start();
            } catch (RuntimeException e) {
                mock.stop();
                users = 0;
                throw e;
            }
        }
        writeAllureMetadata();
    }

    public static synchronized void stop() {
        users--;
        if (users > 0) {
            return;
        }
        if (app != null) {
            app.stop();
            app = null;
        }
        if (mock != null) {
            mock.stop();
            mock = null;
        }
    }

    public static ExternalServiceMock mock() {
        if (mock == null) {
            throw new IllegalStateException("Окружение не запущено");
        }
        return mock;
    }

    private static void writeAllureMetadata() {
        try {
            Path dir = Path.of("target", "allure-results");
            Files.createDirectories(dir);

            Properties env = new Properties();
            env.setProperty("Приложение", TestConfig.appUrl());
            env.setProperty("Внешний сервис (WireMock)", TestConfig.mockUrl());
            env.setProperty("Jar приложения", Path.of(TestConfig.appJar()).getFileName().toString());
            env.setProperty("Java", System.getProperty("java.version"));
            env.setProperty("ОС", System.getProperty("os.name"));
            try (var out = Files.newBufferedWriter(dir.resolve("environment.properties"))) {
                env.store(out, null);
            }

            try (InputStream in = TestEnvironment.class.getResourceAsStream("/allure/categories.json")) {
                if (in != null) {
                    Files.copy(in, dir.resolve("categories.json"), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать метаданные Allure: " + e.getMessage());
        }
    }
}
