package com.testforge.ai.validation;

public final class ValidationFieldPath {

    private ValidationFieldPath() {
    }

    public static String index(String base, int index) {
        return base + "[" + index + "]";
    }

    public static String field(String base, String field) {
        return base + "." + field;
    }

    public static String mapKey(String base, String key) {
        return base + "['" + escape(key) + "']";
    }

    private static String escape(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("\\", "\\\\").replace("'", "\\'");
    }
}
