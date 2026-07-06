package com.testforge.ai.validation;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.testforge.ai.http.SupportedHttpMethods;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.model.TestCaseExpected;
import com.testforge.ai.model.TestCaseRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TestCaseStructuralValidator {

    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .build();

    public TestCaseStructuralValidationResult validate(List<TestCase> testCases) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<TestCase> accepted = new ArrayList<>();
        Set<Integer> invalidIndexes = new HashSet<>();
        Map<Integer, List<ValidationIssue>> issuesByIndex = new HashMap<>();
        boolean batchRejected = false;

        if (testCases == null) {
            issues.add(ValidationIssue.error("TEST_CASE_BATCH_NULL", "testCases",
                    "Test case batch must not be null"));
            return new TestCaseStructuralValidationResult(List.of(), issues, true);
        }

        if (testCases.isEmpty()) {
            issues.add(ValidationIssue.error("TEST_CASE_BATCH_EMPTY", "testCases",
                    "Test case batch must contain at least one test case"));
            return new TestCaseStructuralValidationResult(List.of(), issues, true);
        }

        Map<String, Integer> firstIdIndex = new HashMap<>();
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            String id = testCase != null ? testCase.getId() : null;
            if (!isBlank(id)) {
                Integer firstIndex = firstIdIndex.putIfAbsent(id, i);
                if (firstIndex != null) {
                    batchRejected = true;
                    addIssue(issues, issuesByIndex, i,
                            ValidationIssue.error("DUPLICATE_TEST_CASE_ID", path(i, "id"),
                                    "Duplicate test case id '" + id + "' also appears at testCases[" + firstIndex + "].id"));
                }
            }
        }

        for (int i = 0; i < testCases.size(); i++) {
            validateOne(testCases.get(i), i, issues, issuesByIndex, invalidIndexes);
        }

        if (batchRejected) {
            return new TestCaseStructuralValidationResult(List.of(), issues, true,
                    rejected(testCases, issuesByIndex));
        }

        Set<String> canonicalSeen = new HashSet<>();
        Map<String, Integer> canonicalFirstIndex = new HashMap<>();
        for (int i = 0; i < testCases.size(); i++) {
            if (invalidIndexes.contains(i)) {
                continue;
            }

            TestCase testCase = testCases.get(i);
            String canonical = canonical(testCase);
            Integer firstIndex = canonicalFirstIndex.putIfAbsent(canonical, i);
            if (firstIndex != null || !canonicalSeen.add(canonical)) {
                addIssue(issues, issuesByIndex, i,
                        ValidationIssue.warning("DUPLICATE_EXECUTION_CONTENT", "testCases[" + i + "]",
                                "Execution content duplicates testCases[" + firstIndex + "]; later duplicate rejected"));
                continue;
            }
            accepted.add(testCase);
        }

        return new TestCaseStructuralValidationResult(accepted, issues, false,
                rejected(testCases, issuesByIndex));
    }

    private void validateOne(TestCase testCase, int index, List<ValidationIssue> issues,
                             Map<Integer, List<ValidationIssue>> issuesByIndex,
                             Set<Integer> invalidIndexes) {
        String base = "testCases[" + index + "]";

        if (testCase == null) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.error("TEST_CASE_NULL", base, "Test case must not be null"));
            invalidIndexes.add(index);
            return;
        }

        requireNonBlank(testCase.getId(), path(index, "id"), "TEST_CASE_ID_REQUIRED",
                "Test case id must be present and non-blank", issues, issuesByIndex, index);
        requireNonBlank(testCase.getName(), path(index, "name"), "TEST_CASE_NAME_REQUIRED",
                "Test case name must be present and non-blank", issues, issuesByIndex, index);
        if (testCase.getType() == null) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.error("TEST_CASE_TYPE_REQUIRED", path(index, "type"),
                            "Test case type must be present"));
        }
        if (testCase.getPriority() == null) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.error("TEST_CASE_PRIORITY_REQUIRED", path(index, "priority"),
                            "Test case priority must be present"));
        }
        if (isBlank(testCase.getScenario())) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.warning("TEST_CASE_SCENARIO_MISSING", path(index, "scenario"),
                            "Test case scenario is recommended for display and diagnostics"));
        }

        validateRequest(testCase.getRequest(), index, issues, issuesByIndex);
        validateExpected(testCase.getExpected(), index, issues, issuesByIndex);

        if (hasErrorsForIndex(issuesByIndex, index)) {
            invalidIndexes.add(index);
        }
    }

    private void validateRequest(TestCaseRequest request, int index, List<ValidationIssue> issues,
                                 Map<Integer, List<ValidationIssue>> issuesByIndex) {
        if (request == null) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.error("REQUEST_REQUIRED", path(index, "request"),
                            "Request object must be present"));
            return;
        }

        if (isBlank(request.getMethod())) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.error("REQUEST_METHOD_REQUIRED", path(index, "request.method"),
                            "Request method must be present and non-blank"));
        } else {
            if (!SupportedHttpMethods.isSupported(request.getMethod())) {
                addIssue(issues, issuesByIndex, index,
                        ValidationIssue.error("UNSUPPORTED_HTTP_METHOD", path(index, "request.method"),
                                "HTTP method '" + request.getMethod() + "' is not supported by the current OpenAPI loader/executor path"));
            }
        }

        requireNonBlank(request.getPath(), path(index, "request.path"), "REQUEST_PATH_REQUIRED",
                "Request path must be present and non-blank", issues, issuesByIndex, index);

        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                String headerPath = ValidationFieldPath.mapKey(path(index, "request.headers"), entry.getKey());
                if (isBlank(entry.getKey())) {
                    addIssue(issues, issuesByIndex, index,
                            ValidationIssue.error("REQUEST_HEADER_NAME_REQUIRED", headerPath,
                                    "Request header name must be present and non-blank"));
                }
                if (entry.getValue() == null) {
                    addIssue(issues, issuesByIndex, index,
                            ValidationIssue.error("REQUEST_HEADER_VALUE_REQUIRED", headerPath,
                                    "Request header value must not be null"));
                }
            }
        }
    }

    private void validateExpected(TestCaseExpected expected, int index, List<ValidationIssue> issues,
                                  Map<Integer, List<ValidationIssue>> issuesByIndex) {
        if (expected == null) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.error("EXPECTED_REQUIRED", path(index, "expected"),
                            "Expected result object must be present"));
            return;
        }

        int status = expected.getStatus();
        if (status < 100 || status > 599) {
            addIssue(issues, issuesByIndex, index,
                    ValidationIssue.error("EXPECTED_STATUS_INVALID", path(index, "expected.status"),
                            "Expected HTTP status must be between 100 and 599"));
        }

        if (expected.getBodyAssertions() != null) {
            for (Map.Entry<String, Object> entry : expected.getBodyAssertions().entrySet()) {
                String assertionPath = ValidationFieldPath.mapKey(path(index, "expected.bodyAssertions"), entry.getKey());
                if (isBlank(entry.getKey())) {
                    addIssue(issues, issuesByIndex, index,
                            ValidationIssue.error("BODY_ASSERTION_FIELD_REQUIRED", assertionPath,
                                    "Body assertion field name must be present and non-blank"));
                }
                if (entry.getValue() == null) {
                    addIssue(issues, issuesByIndex, index,
                            ValidationIssue.error("BODY_ASSERTION_EXPECTED_REQUIRED", assertionPath,
                                    "Body assertion expected value must not be null"));
                }
            }
        }
    }

    private void requireNonBlank(String value, String fieldPath, String code, String message,
                                 List<ValidationIssue> issues,
                                 Map<Integer, List<ValidationIssue>> issuesByIndex,
                                 int index) {
        if (isBlank(value)) {
            addIssue(issues, issuesByIndex, index, ValidationIssue.error(code, fieldPath, message));
        }
    }

    private void addIssue(List<ValidationIssue> issues, Map<Integer, List<ValidationIssue>> issuesByIndex,
                          int index, ValidationIssue issue) {
        issues.add(issue);
        issuesByIndex.computeIfAbsent(index, ignored -> new ArrayList<>()).add(issue);
    }

    private boolean hasErrorsForIndex(Map<Integer, List<ValidationIssue>> issuesByIndex, int index) {
        List<ValidationIssue> itemIssues = issuesByIndex.get(index);
        if (itemIssues == null) {
            return false;
        }
        return itemIssues.stream().anyMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR);
    }

    private List<RejectedTestCase> rejected(List<TestCase> testCases,
                                            Map<Integer, List<ValidationIssue>> issuesByIndex) {
        return issuesByIndex.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR
                                || "DUPLICATE_EXECUTION_CONTENT".equals(issue.getCode())))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RejectedTestCase(
                        entry.getKey(),
                        testCaseId(testCases, entry.getKey()),
                        entry.getValue()))
                .toList();
    }

    private String testCaseId(List<TestCase> testCases, int index) {
        if (testCases == null || index < 0 || index >= testCases.size() || testCases.get(index) == null) {
            return null;
        }
        return testCases.get(index).getId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String path(int index, String field) {
        return "testCases[" + index + "]." + field;
    }

    private String canonical(TestCase testCase) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        TestCaseRequest request = testCase.getRequest();
        TestCaseExpected expected = testCase.getExpected();

        canonical.put("method", request.getMethod().trim().toUpperCase(Locale.ROOT));
        canonical.put("path", request.getPath());
        canonical.put("headers", canonicalHeaders(request.getHeaders()));
        canonical.put("body", canonicalValue(request.getBody()));
        canonical.put("expectedStatus", expected.getStatus());
        canonical.put("bodyAssertions", canonicalValue(
                expected.getBodyAssertions() != null ? expected.getBodyAssertions() : Map.of()));

        try {
            return CANONICAL_MAPPER.writeValueAsString(canonicalValue(canonical));
        } catch (Exception e) {
            throw new RuntimeException("Failed to canonicalize test case: " + testCase.getId(), e);
        }
    }

    private Object canonicalHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, String> normalized = new TreeMap<>();
        headers.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Object canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new TreeMap<>();
            map.forEach((key, mapValue) -> normalized.put(String.valueOf(key), canonicalValue(mapValue)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::canonicalValue).toList();
        }
        return value;
    }
}
