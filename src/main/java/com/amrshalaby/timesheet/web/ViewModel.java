package com.amrshalaby.timesheet.web;

import io.micronaut.session.Session;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ViewModel {
    private static final String CSRF_SESSION_KEY = "csrfToken";

    private ViewModel() {
    }

    public static Map<String, Object> withCsrf(Map<String, Object> model, Session session) {
        Map<String, Object> enriched = new LinkedHashMap<>(model);
        if (session != null) {
            session.get(CSRF_SESSION_KEY, String.class)
                .ifPresent(token -> enriched.put(CSRF_SESSION_KEY, token));
        }
        return enriched;
    }
}
