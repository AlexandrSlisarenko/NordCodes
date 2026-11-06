package com.example.aqa.data;

import java.util.UUID;

public class TokenFactory {
    public static String random() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
