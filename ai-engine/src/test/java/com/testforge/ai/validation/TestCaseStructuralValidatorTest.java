package com.testforge.ai.validation;

import com.testforge.ai.model.Priority;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.model.TestCaseExpected;
import com.testforge.ai.model.TestCaseRequest;
import com.testforge.ai.model.TestCaseType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCaseStructuralValidatorTest {

    private final TestCaseStructuralValidator validator = new TestCaseStructuralValidator();

    @Test
    void validTestCase_isAccepted() {
        TestCaseStructuralValidationResult result = validator.validate(List.of(valid("tc-1")));

        assertFalse(result.isBatchRejected());
        assertEquals(List.of(valid("tc-1")).size(), result.getAcceptedTestCases().size());
        assertTrue(result.getIssues().isEmpty());
    }

    @Test
    void requiredFieldsMissing_reportsAllItemErrors() {
        TestCase testCase = valid("tc-1");
        testCase.setName(" ");
        testCase.setType(null);
        testCase.setPriority(null);
        testCase.setScenario("");
        testCase.setRequest(null);
        testCase.setExpected(null);

        TestCaseStructuralValidationResult result = validator.validate(List.of(testCase));

        assertFalse(result.isBatchRejected());
        assertTrue(result.getAcceptedTestCases().isEmpty());
        assertCodes(result,
                "TEST_CASE_NAME_REQUIRED",
                "TEST_CASE_TYPE_REQUIRED",
                "TEST_CASE_PRIORITY_REQUIRED",
                "TEST_CASE_SCENARIO_MISSING",
                "REQUEST_REQUIRED",
                "EXPECTED_REQUIRED");
        assertTrue(result.warnings().stream()
                .anyMatch(issue -> "TEST_CASE_SCENARIO_MISSING".equals(issue.getCode())));
    }

    @Test
    void blankId_isItemError() {
        TestCase testCase = valid(" ");

        TestCaseStructuralValidationResult result = validator.validate(List.of(testCase));

        assertFalse(result.isBatchRejected());
        assertTrue(result.getAcceptedTestCases().isEmpty());
        assertCodes(result, "TEST_CASE_ID_REQUIRED");
        assertEquals("testCases[0].id", result.errors().get(0).getFieldPath());
    }

    @Test
    void duplicateIds_rejectEntireBatch() {
        TestCaseStructuralValidationResult result = validator.validate(List.of(valid("tc-1"), valid("tc-1")));

        assertTrue(result.isBatchRejected());
        assertTrue(result.getAcceptedTestCases().isEmpty());
        assertCodes(result, "DUPLICATE_TEST_CASE_ID");
    }

    @Test
    void unsupportedHttpMethod_isRejected() {
        TestCase testCase = valid("tc-1");
        testCase.getRequest().setMethod("TRACE");

        TestCaseStructuralValidationResult result = validator.validate(List.of(testCase));

        assertTrue(result.getAcceptedTestCases().isEmpty());
        assertCodes(result, "UNSUPPORTED_HTTP_METHOD");
    }

    @Test
    void headMethod_isAccepted() {
        TestCase testCase = valid("tc-1");
        testCase.getRequest().setMethod("HEAD");
        testCase.getRequest().setBody(null);

        TestCaseStructuralValidationResult result = validator.validate(List.of(testCase));

        assertEquals(1, result.getAcceptedTestCases().size());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void invalidExpectedStatus_isRejected() {
        TestCase testCase = valid("tc-1");
        testCase.getExpected().setStatus(0);

        TestCaseStructuralValidationResult result = validator.validate(List.of(testCase));

        assertTrue(result.getAcceptedTestCases().isEmpty());
        assertCodes(result, "EXPECTED_STATUS_INVALID");
    }

    @Test
    void invalidRequestAndExpectedStructures_areRejected() {
        TestCase testCase = valid("tc-1");
        testCase.getRequest().setPath(" ");
        testCase.getRequest().setHeaders(new java.util.HashMap<>());
        testCase.getRequest().getHeaders().put("", "value");
        testCase.getRequest().getHeaders().put("X-Test", null);
        testCase.getExpected().setBodyAssertions(new java.util.HashMap<>());
        testCase.getExpected().getBodyAssertions().put("", "value");
        testCase.getExpected().getBodyAssertions().put("id", null);

        TestCaseStructuralValidationResult result = validator.validate(List.of(testCase));

        assertTrue(result.getAcceptedTestCases().isEmpty());
        assertCodes(result,
                "REQUEST_PATH_REQUIRED",
                "REQUEST_HEADER_NAME_REQUIRED",
                "REQUEST_HEADER_VALUE_REQUIRED",
                "BODY_ASSERTION_FIELD_REQUIRED",
                "BODY_ASSERTION_EXPECTED_REQUIRED");
    }

    @Test
    void mapKeyFieldPathsAreEscaped() {
        TestCase testCase = valid("tc-1");
        testCase.getRequest().setHeaders(new java.util.HashMap<>());
        testCase.getRequest().getHeaders().put("X Weird 'Header\\Name", null);
        testCase.getExpected().setBodyAssertions(new java.util.HashMap<>());
        testCase.getExpected().getBodyAssertions().put("field.with 'quote\\slash", null);

        TestCaseStructuralValidationResult result = validator.validate(List.of(testCase));

        List<String> paths = result.getIssues().stream()
                .map(ValidationIssue::getFieldPath)
                .toList();
        assertTrue(paths.contains("testCases[0].request.headers['X Weird \\'Header\\\\Name']"), paths.toString());
        assertTrue(paths.contains("testCases[0].expected.bodyAssertions['field.with \\'quote\\\\slash']"), paths.toString());
    }

    @Test
    void duplicateExecutionContent_rejectsLaterDuplicateWithWarningOnly() {
        TestCase first = valid("tc-1");
        TestCase second = valid("tc-2");
        second.setName("Different display name");
        second.setScenario("Different scenario text");

        TestCaseStructuralValidationResult result = validator.validate(List.of(first, second));

        assertFalse(result.isBatchRejected());
        assertFalse(result.hasErrors());
        assertEquals(1, result.getAcceptedTestCases().size());
        assertEquals("tc-1", result.getAcceptedTestCases().get(0).getId());
        assertEquals(1, result.warnings().size());
        assertEquals("DUPLICATE_EXECUTION_CONTENT", result.warnings().get(0).getCode());
        assertEquals("testCases[1]", result.warnings().get(0).getFieldPath());
        assertEquals(1, result.getRejectedTestCases().size());
        assertEquals(1, result.getRejectedTestCases().get(0).getBatchIndex());
        assertEquals("tc-2", result.getRejectedTestCases().get(0).getTestCaseId());
    }

    @Test
    void oneInvalidItem_keepsValidItems() {
        TestCase invalid = valid("tc-invalid");
        invalid.getExpected().setStatus(700);
        TestCase valid = valid("tc-valid");
        valid.getRequest().setPath("/api/payments/pay-1");
        valid.getRequest().setMethod("GET");
        valid.getRequest().setBody(null);

        TestCaseStructuralValidationResult result = validator.validate(List.of(invalid, valid));

        assertFalse(result.isBatchRejected());
        assertEquals(1, result.getAcceptedTestCases().size());
        assertEquals("tc-valid", result.getAcceptedTestCases().get(0).getId());
        assertCodes(result, "EXPECTED_STATUS_INVALID");
    }

    @Test
    void allInvalidItems_returnsNoAcceptedItems() {
        TestCase first = valid("tc-1");
        first.setRequest(null);
        TestCase second = valid("tc-2");
        second.setExpected(null);

        TestCaseStructuralValidationResult result = validator.validate(List.of(first, second));

        assertFalse(result.isBatchRejected());
        assertTrue(result.getAcceptedTestCases().isEmpty());
        assertCodes(result, "REQUEST_REQUIRED", "EXPECTED_REQUIRED");
    }

    @Test
    void nullAndEmptyBatch_areBatchErrors() {
        TestCaseStructuralValidationResult nullResult = validator.validate(null);
        TestCaseStructuralValidationResult emptyResult = validator.validate(List.of());

        assertTrue(nullResult.isBatchRejected());
        assertTrue(emptyResult.isBatchRejected());
        assertCodes(nullResult, "TEST_CASE_BATCH_NULL");
        assertCodes(emptyResult, "TEST_CASE_BATCH_EMPTY");
    }

    private void assertCodes(TestCaseStructuralValidationResult result, String... expectedCodes) {
        List<String> codes = result.getIssues().stream()
                .map(ValidationIssue::getCode)
                .toList();
        for (String expectedCode : expectedCodes) {
            assertTrue(codes.contains(expectedCode), "Expected issue code " + expectedCode + " in " + codes);
        }
    }

    private TestCase valid(String id) {
        TestCaseRequest request = new TestCaseRequest();
        request.setMethod("POST");
        request.setPath("/api/payments");
        request.setHeaders(Map.of("Content-Type", "application/json"));
        request.setBody(Map.of("amount", 100, "currency", "USD"));

        TestCaseExpected expected = new TestCaseExpected();
        expected.setStatus(201);
        expected.setBodyAssertions(Map.of("id", "non-null", "status", "COMPLETED"));

        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setName("Create payment");
        testCase.setType(TestCaseType.HAPPY_PATH);
        testCase.setPriority(Priority.P0);
        testCase.setScenario("Given a valid payment When POST /api/payments Then 201");
        testCase.setRequest(request);
        testCase.setExpected(expected);
        testCase.setReasoning("Covers the core happy path");
        return testCase;
    }
}
