package com.example.aqa.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Настройки тестов: config.properties, любое значение можно переопределить через -Dключ=значение. */
public final class TestConfig {

    private static final Properties PROPS = load();

    private TestConfig() {
    }

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = TestConfig.class.getResourceAsStream("/config.properties")) {
            if (in == null) {
                throw new IllegalStateException("Не найден файл config.properties в test resources");
            }
            props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать config.properties", e);
        }
        return props;
    }

    private static String get(String key) {
        String fromSystem = System.getProperty(key);
        return fromSystem != null ? fromSystem : PROPS.getProperty(key);
    }

    public static String appUrl() {
        return get("app.url");
    }

    public static String apiKey() {
        return get("app.secret");
    }

    public static String appJar() {
        return get("app.jar");
    }

    public static boolean startApp() {
        return Boolean.parseBoolean(get("app.start"));
    }

    public static int startupTimeoutSeconds() {
        return Integer.parseInt(get("app.startup-timeout-seconds"));
    }

    public static int mockPort() {
        return Integer.parseInt(get("mock.port"));
    }

    public static String mockUrl() {
        return "http://localhost:" + mockPort() + "/";
    }

    public static int clientTimeoutSeconds() {
        return Integer.parseInt(get("client.timeout-seconds"));
    }
}
