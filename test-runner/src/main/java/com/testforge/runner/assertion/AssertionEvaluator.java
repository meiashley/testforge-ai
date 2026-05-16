package com.testforge.runner.assertion;

import com.testforge.ai.scenario.Assertion;
import com.testforge.runner.execution.JsonPathExtractor;
import com.testforge.runner.model.AssertionResult;
import com.testforge.runner.model.HttpResponse;

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

    public List<com.testforge.runner.execution.AssertionResult> evaluateV5(
            List<Assertion> assertions, HttpResponse response) {
        if (assertions == null || assertions.isEmpty()) return List.of();
        List<com.testforge.runner.execution.AssertionResult> results = new ArrayList<>();
        for (Assertion assertion : assertions) {
            results.add(evaluateV5One(assertion, response));
        }
        return results;
    }

    private com.testforge.runner.execution.AssertionResult evaluateV5One(
            Assertion assertion, HttpResponse response) {
        Object actual = JsonPathExtractor.extract(response, assertion.getPath());
        String actualStr = actual != null ? String.valueOf(actual) : null;

        boolean passed;
        String message = null;

        switch (assertion.getType()) {
            case "EQUALS" -> {
                String expectedStr = assertion.getExpected() != null
                        ? String.valueOf(assertion.getExpected()) : null;
                passed = Objects.equals(actualStr, expectedStr);
                if (!passed) message = "expected " + expectedStr + " but was " + actualStr;
            }
            case "NOT_EQUALS" -> {
                String expectedStr = assertion.getExpected() != null
                        ? String.valueOf(assertion.getExpected()) : null;
                passed = !Objects.equals(actualStr, expectedStr);
                if (!passed) message = "expected not " + expectedStr + " but was equal";
            }
            case "EXISTS" -> {
                passed = actual != null;
                if (!passed) message = "expected field to exist at " + assertion.getPath();
            }
            case "NOT_EXISTS" -> {
                passed = actual == null;
                if (!passed) message = "expected field to not exist but found " + actualStr;
            }
            case "CONTAINS" -> {
                String expectedStr = assertion.getExpected() != null
                        ? String.valueOf(assertion.getExpected()) : "";
                passed = actualStr != null && actualStr.contains(expectedStr);
                if (!passed) message = "expected " + actualStr + " to contain " + expectedStr;
            }
            default -> {
                passed = false;
                message = "unknown assertion type: " + assertion.getType();
            }
        }

        return com.testforge.runner.execution.AssertionResult.builder()
                .assertion(assertion)
                .passed(passed)
                .actualValue(actualStr)
                .message(message)
                .build();
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
