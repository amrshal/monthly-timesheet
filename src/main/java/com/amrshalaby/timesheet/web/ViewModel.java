package com.amrshalaby.timesheet.web;

import io.micronaut.session.Session;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ViewModel {
    private static final String CSRF_SESSION_KEY = "csrfToken";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ViewModel() {
    }

    public static Map<String, Object> withCsrf(Map<String, Object> model, Session session) {
        Map<String, Object> enriched = new LinkedHashMap<>(model);
        if (session != null) {
            String token = session.get(CSRF_SESSION_KEY, String.class).orElseGet(ViewModel::newCsrfToken);
            session.put(CSRF_SESSION_KEY, token);
            enriched.put(CSRF_SESSION_KEY, token);
        }
        return enriched;
    }

    private static String newCsrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
