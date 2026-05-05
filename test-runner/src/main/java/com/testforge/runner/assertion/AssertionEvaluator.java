package com.testforge.runner.assertion;

import com.testforge.runner.model.AssertionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AssertionEvaluator {

    public List<AssertionResult> evaluate(Map<String, Object> bodyAssertions, Map<String, Object> actualBody) {
        if (bodyAssertions == null || bodyAssertions.isEmpty()) {
            return List.of();
        }

        List<AssertionResult> results = new ArrayList<>();
        for (Map.Entry<String, Object> entry : bodyAssertions.entrySet()) {
            String field = entry.getKey();
            Object expected = entry.getValue();
            results.add(evaluateOne(field, expected, actualBody));
        }
        return results;
    }

    private AssertionResult evaluateOne(String field, Object expected, Map<String, Object> actualBody) {
        if (!actualBody.containsKey(field)) {
            return new AssertionResult(field, expected, null, false, "field absent");
        }

        Object actual = actualBody.get(field);

        if ("non-null".equals(expected)) {
            if (actual == null) {
                return new AssertionResult(field, expected, null, false, "expected non-null but was null");
            }
            return new AssertionResult(field, expected, actual, true, null);
        }

        if (actual == null) {
            return new AssertionResult(field, expected, null, false,
                    "expected " + expected + " but was null");
        }

        if (!expected.getClass().equals(actual.getClass())) {
            String reason = "type mismatch: expected " + expected.getClass().getSimpleName()
                    + " but was " + actual.getClass().getSimpleName();
            return new AssertionResult(field, expected, actual, false, reason);
        }

        if (Objects.equals(expected, actual)) {
            return new AssertionResult(field, expected, actual, true, null);
        }

        return new AssertionResult(field, expected, actual, false,
                "expected " + expected + " but was " + actual);
    }
}
