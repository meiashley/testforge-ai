package com.testforge.ai.analysis;

import com.testforge.ai.client.ClaudeClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FailureAnalyzerTest {

    private static final String SINGLE_RESULT_JSON = """
            [
              {
                "test_case_id": "tc-001",
                "root_cause_category": "ASSERTION_TOO_STRICT",
                "root_cause_summary": "Test expects a 'code' field that the API error response does not include.",
                "evidence": "actual_response_body has no 'code' key; assertion_failures: field 'code': field absent",
                "suggested_fix": "Remove the 'code' assertion or update it to match the actual error response schema.",
                "confidence": "HIGH"
              }
            ]
            """;

    private static final String THREE_RESULTS_JSON = """
            [
              {
                "test_case_id": "tc-001",
                "root_cause_category": "ASSERTION_TOO_STRICT",
                "root_cause_summary": "Test expects 'code' field absent from error response.",
                "evidence": "field 'code': field absent in assertion failures",
                "suggested_fix": "Remove 'code' assertion from expected body.",
                "confidence": "HIGH"
              },
              {
                "test_case_id": "tc-002",
                "root_cause_category": "TEST_LOGIC_ERROR",
                "root_cause_summary": "Boundary test incorrectly expects 201 for a value the API rejects.",
                "evidence": "actual_status 400 vs expected_status 201",
                "suggested_fix": "Update expected status to 400 for maxLength boundary violation.",
                "confidence": "MEDIUM"
              },
              {
                "test_case_id": "tc-003",
                "root_cause_category": "API_BUG",
                "root_cause_summary": "API does not return 'description' field in GET response despite spec.",
                "evidence": "field 'description': field absent in actual response body",
                "suggested_fix": "Fix API to include 'description' in payment response.",
                "confidence": "HIGH"
              }
            ]
            """;

    private FailureAnalysisInput sampleInput(String id, String name) {
        return FailureAnalysisInput.builder()
                .testCaseId(id)
                .testCaseName(name)
                .testCaseType("NEGATIVE")
                .endpointMethod("POST")
                .endpointPath("/api/payments")
                .requestBody("{\"amount\": \"one-hundred\"}")
                .expectedStatusCode(400)
                .actualStatusCode(400)
                .actualResponseBody("{\"error\": \"Bad Request\"}")
                .assertionFailures("field 'code': field absent")
                .build();
    }

    // Test 1: empty list → empty result
    @Test
    void emptyInput_returnsEmptyList() {
        ClaudeClient neverCalled = prompt -> { throw new AssertionError("Should not call Claude for empty input"); };
        FailureAnalyzer analyzer = new FailureAnalyzer(neverCalled);

        List<FailureAnalysisResult> results = analyzer.analyze(List.of());

        assertTrue(results.isEmpty());
    }

    // Test 2: single failure → 1 result with correct fields
    @Test
    void singleFailure_returnsOneResult() {
        ClaudeClient mock = prompt -> SINGLE_RESULT_JSON;
        FailureAnalyzer analyzer = new FailureAnalyzer(mock);

        List<FailureAnalysisResult> results = analyzer.analyze(
                List.of(sampleInput("tc-001", "Create payment with amount as string"))
        );

        assertEquals(1, results.size());
        FailureAnalysisResult r = results.get(0);
        assertEquals("tc-001", r.getTestCaseId());
        assertEquals("Create payment with amount as string", r.getTestCaseName());
        assertEquals("ASSERTION_TOO_STRICT", r.getRootCauseCategory());
        assertEquals("HIGH", r.getConfidence());
        assertNotNull(r.getRootCauseSummary());
        assertNotNull(r.getEvidence());
        assertNotNull(r.getSuggestedFix());
    }

    // Test 3: batch 3 failures → 3 results
    @Test
    void batchThreeFailures_returnsThreeResults() {
        ClaudeClient mock = prompt -> THREE_RESULTS_JSON;
        FailureAnalyzer analyzer = new FailureAnalyzer(mock);

        List<FailureAnalysisInput> inputs = List.of(
                sampleInput("tc-001", "Create payment with amount as string"),
                sampleInput("tc-002", "Create payment with description at maxLength boundary"),
                sampleInput("tc-003", "Response body contains all defined fields for existing payment")
        );

        List<FailureAnalysisResult> results = analyzer.analyze(inputs);

        assertEquals(3, results.size());
        assertEquals("tc-001", results.get(0).getTestCaseId());
        assertEquals("ASSERTION_TOO_STRICT", results.get(0).getRootCauseCategory());
        assertEquals("tc-002", results.get(1).getTestCaseId());
        assertEquals("TEST_LOGIC_ERROR", results.get(1).getRootCauseCategory());
        assertEquals("tc-003", results.get(2).getTestCaseId());
        assertEquals("API_BUG", results.get(2).getRootCauseCategory());
    }

    // Test 4: malformed JSON → throws RuntimeException
    @Test
    void malformedJson_throwsRuntimeException() {
        ClaudeClient mock = prompt -> "not valid json at all {{{{";
        FailureAnalyzer analyzer = new FailureAnalyzer(mock);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> analyzer.analyze(List.of(sampleInput("tc-001", "some case"))));
        assertTrue(ex.getMessage().contains("Failed to parse FailureAnalyzer response"));
    }

    // Test 5: Claude returns fewer results than input → no padding, return only what came back
    @Test
    void claudeReturnsFewer_returnsOnlyActualResults() {
        // Claude returns 1 result for 3 inputs — should NOT pad with dummy data
        ClaudeClient mock = prompt -> SINGLE_RESULT_JSON;
        FailureAnalyzer analyzer = new FailureAnalyzer(mock);

        List<FailureAnalysisInput> inputs = List.of(
                sampleInput("tc-001", "Case one"),
                sampleInput("tc-002", "Case two"),
                sampleInput("tc-003", "Case three")
        );

        List<FailureAnalysisResult> results = analyzer.analyze(inputs);

        assertEquals(1, results.size(), "Should return only what Claude actually provided, no padding");
        assertEquals("tc-001", results.get(0).getTestCaseId());
    }
}
