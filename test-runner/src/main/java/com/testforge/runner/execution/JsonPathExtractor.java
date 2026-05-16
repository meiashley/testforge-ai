package com.testforge.runner.execution;

import com.testforge.runner.model.HttpResponse;

public class JsonPathExtractor {

    public static Object extract(HttpResponse response, String jsonPath) {
        if (jsonPath == null || response == null) return null;

        if (jsonPath.startsWith("$.body.") && response.getBody() != null) {
            String field = jsonPath.substring("$.body.".length());
            return response.getBody().get(field);
        }

        if (jsonPath.startsWith("$.headers.") && response.getHeaders() != null) {
            String header = jsonPath.substring("$.headers.".length());
            return response.getHeaders().get(header);
        }

        if ("$.body".equals(jsonPath)) {
            return response.getRawBody();
        }

        return null;
    }
}
