package com.testforge.ai.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.testforge.ai.client.ClaudeClient;

import java.util.List;
import java.util.Map;

public class FailureAnalyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private final ClaudeClient claudeClient;

    public FailureAnalyzer(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public List<FailureAnalysisResult> analyze(List<FailureAnalysisInput> failures) {
        if (failures.isEmpty()) return List.of();
        String prompt = buildBatchPrompt(failures);
        String response = claudeClient.generate(prompt);
        return parseResults(response, failures);
    }

    private String buildBatchPrompt(List<FailureAnalysisInput> failures) {
        StringBuilder cases = new StringBuilder();
        for (int i = 0; i < failures.size(); i++) {
            FailureAnalysisInput f = failures.get(i);
            cases.append("=== Failure ").append(i + 1).append(" ===\n");
            cases.append("test_case_id: ").append(f.getTestCaseId()).append("\n");
            cases.append("test_case_name: ").append(f.getTestCaseName()).append("\n");
            cases.append("test_case_type: ").append(f.getTestCaseType()).append("\n");
            cases.append("endpoint: ").append(f.getEndpointMethod()).append(" ").append(f.getEndpointPath()).append("\n");
            cases.append("request_body: ").append(f.getRequestBody()).append("\n");
            cases.append("expected_status: ").append(f.getExpectedStatusCode()).append("\n");
            cases.append("actual_status: ").append(f.getActualStatusCode()).append("\n");
            cases.append("actual_response_body: ").append(f.getActualResponseBody()).append("\n");
            cases.append("assertion_failures: ").append(f.getAssertionFailures()).append("\n");
            cases.append("\n");
        }

        return """
                You are an expert API test engineer. Analyze the following failed test cases \
                and diagnose the root cause for each.

                For each failure, return a JSON object with these fields:
                - test_case_id: string (preserve the input id)
                - root_cause_category: one of [TEST_LOGIC_ERROR, API_BUG, DATA_DEPENDENCY, \
                ASSERTION_TOO_STRICT, ENVIRONMENT, UNCERTAIN]
                - root_cause_summary: one-sentence explanation
                - evidence: specific data point from the request/response showing why
                - suggested_fix: concrete recommendation
                - confidence: HIGH / MEDIUM / LOW (HIGH if evidence is clear; LOW if ambiguous)

                Categories defined:
                - TEST_LOGIC_ERROR: The test case itself has wrong expectations
                - API_BUG: The API behaves incorrectly per the spec
                - DATA_DEPENDENCY: Required prerequisite data is missing/wrong state
                - ASSERTION_TOO_STRICT: Assertions check fields not actually required
                - ENVIRONMENT: Network/timeout/infrastructure issue
                - UNCERTAIN: Multiple plausible causes; needs human review

                Return ONLY a JSON array, no surrounding text. Structure:
                [
                  {
                    "test_case_id": "tc-001",
                    "root_cause_category": "TEST_LOGIC_ERROR",
                    "root_cause_summary": "...",
                    "evidence": "...",
                    "suggested_fix": "...",
                    "confidence": "HIGH"
                  },
                  ...
                ]

                Failures to analyze:
                """ + cases;
    }

    private List<FailureAnalysisResult> parseResults(String response, List<FailureAnalysisInput> failures) {
        try {
            String cleaned = stripMarkdownFence(response);
            List<Map<String, String>> raw = MAPPER.readValue(cleaned, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> FailureAnalysisResult.builder()
                            .testCaseId(m.get("test_case_id"))
                            .testCaseName(findName(m.get("test_case_id"), failures))
                            .rootCauseCategory(m.get("root_cause_category"))
                            .rootCauseSummary(m.get("root_cause_summary"))
                            .evidence(m.get("evidence"))
                            .suggestedFix(m.get("suggested_fix"))
                            .confidence(m.get("confidence"))
                            .build())
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse FailureAnalyzer response: " + e.getMessage(), e);
        }
    }

    private String findName(String id, List<FailureAnalysisInput> failures) {
        return failures.stream()
                .filter(f -> f.getTestCaseId().equals(id))
                .map(FailureAnalysisInput::getTestCaseName)
                .findFirst()
                .orElse("");
    }

    private String stripMarkdownFence(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                String body = trimmed.substring(firstNewline + 1);
                if (body.endsWith("```")) {
                    body = body.substring(0, body.length() - 3);
                }
                return body.strip();
            }
        }
        return trimmed;
    }
}
