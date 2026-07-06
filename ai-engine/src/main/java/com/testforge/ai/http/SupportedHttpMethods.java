package com.testforge.ai.http;

import java.util.Locale;
import java.util.Set;

public final class SupportedHttpMethods {

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");

    private SupportedHttpMethods() {
    }

    public static boolean isSupported(String method) {
        return method != null && METHODS.contains(normalize(method));
    }

    public static String normalize(String method) {
        return method == null ? null : method.trim().toUpperCase(Locale.ROOT);
    }

    public static Set<String> all() {
        return METHODS;
    }
}
