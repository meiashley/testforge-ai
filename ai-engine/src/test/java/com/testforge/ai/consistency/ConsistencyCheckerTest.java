package com.testforge.ai.consistency;

import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.requirement.RequirementAnalysis;
import com.testforge.ai.requirement.RequirementConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencyCheckerTest {

    private static final String SINGLE_MISMATCH_JSON = """
            [
              {
                "mismatchId": "mm-001",
                "category": "MISSING_IN_API",
                "severity": "HIGH",
                "confidence": "HIGH",
                "summary": "Cross-user refund check absent in API spec",
                "evidence": "Section 4.3 mandates payerId match; spec has no such rule",
                "location": "POST /api/payments/{id}/refund",
                "requirementReference": "Section 4.3",
                "specReference": "/api/payments/{id}/refund"
              }
            ]
            """;

    private static final String THREE_MISMATCHES_JSON = """
            [
              {
                "mismatchId": "mm-001",
                "category": "MISSING_IN_API",
                "severity": "HIGH",
                "confidence": "HIGH",
                "summary": "Ownership check missing",
                "evidence": "Section 4.3 not in spec",
                "location": "POST /api/payments/{id}/refund",
                "requirementReference": "Section 4.3",
                "specReference": "/api/payments/{id}/refund"
              },
              {
                "mismatchId": "mm-002",
                "category": "CONSTRAINT_CONFLICT",
                "severity": "MEDIUM",
                "confidence": "HIGH",
                "summary": "Amount max differs",
                "evidence": "Req says 100000, spec says 99999",
                "location": "POST /api/payments → amount",
                "requirementReference": "Section 2.1",
                "specReference": "#/components/schemas/PaymentRequest/amount"
              },
              {
                "mismatchId": "mm-003",
                "category": "UNDOCUMENTED_API",
                "severity": "LOW",
                "confidence": "MEDIUM",
                "summary": "Extra error code in spec",
                "evidence": "Spec returns RATE_LIMITED not in requirements",
                "location": "POST /api/payments",
                "requirementReference": "Section 2.1",
                "specReference": "/api/payments"
              }
            ]
            """;

    private RequirementConstraint constraint(String id, String section) {
        return RequirementConstraint.builder()
                .constraintId(id)
                .category("VALUE_RANGE")
                .subject("amount")
                .description("some constraint")
                .requirementSection(section)
                .build();
    }

    private RequirementAnalysis analysisWithConstraints(List<RequirementConstraint> constraints) {
        return RequirementAnalysis.builder()
                .constraints(constraints)
                .identifiedScenarios(List.of("Happy Path"))
                .build();
    }

    @Test
    void check_emptyConstraints_returnsEmptyResultWithoutCallingClaude() {
        ClaudeClient neverCalled = prompt -> { throw new AssertionError("Should not call Claude for empty constraints"); };
        ConsistencyChecker checker = new ConsistencyChecker(neverCalled);

        RequirementAnalysis empty = RequirementAnalysis.builder().build();
        AlignmentResult result = checker.check(empty, "openapi: 3.0.0");

        assertNotNull(result);
        assertEquals(0, result.getTotalConstraints());
        assertEquals(0, result.getAlignedCount());
        assertEquals(0, result.getMismatchCount());
        assertTrue(result.getMismatches().isEmpty());
        assertTrue(result.getSeverityBreakdown().isEmpty());
        assertTrue(result.getCategoryBreakdown().isEmpty());
    }

    @Test
    void check_singleMismatch_computesCountsCorrectly() {
        ClaudeClient mock = prompt -> SINGLE_MISMATCH_JSON;
        ConsistencyChecker checker = new ConsistencyChecker(mock);

        // 3 constraints, 1 references "Section 4.3" which matches the mismatch
        RequirementAnalysis analysis = analysisWithConstraints(List.of(
                constraint("req-1", "Section 2.1"),
                constraint("req-2", "Section 2.2"),
                constraint("req-3", "Section 4.3")
        ));

        AlignmentResult result = checker.check(analysis, "openapi: 3.0.0");

        assertEquals(3, result.getTotalConstraints());
        assertEquals(1, result.getMismatchCount());
        assertEquals(2, result.getAlignedCount());

        ConsistencyMismatch m = result.getMismatches().get(0);
        assertEquals("mm-001", m.getMismatchId());
        assertEquals("MISSING_IN_API", m.getCategory());
        assertEquals("HIGH", m.getSeverity());
        assertEquals("HIGH", m.getConfidence());

        assertEquals(Map.of("HIGH", 1), result.getSeverityBreakdown());
        assertEquals(Map.of("MISSING_IN_API", 1), result.getCategoryBreakdown());
    }

    @Test
    void check_multipleMismatches_severityBreakdownCorrect() {
        ClaudeClient mock = prompt -> THREE_MISMATCHES_JSON;
        ConsistencyChecker checker = new ConsistencyChecker(mock);

        // constraints covering sections referenced in mismatches
        RequirementAnalysis analysis = analysisWithConstraints(List.of(
                constraint("req-1", "Section 2.1"),
                constraint("req-2", "Section 4.3"),
                constraint("req-3", "Section 3.1"),
                constraint("req-4", "Section 5.1")
        ));

        AlignmentResult result = checker.check(analysis, "openapi: 3.0.0");

        assertEquals(4, result.getTotalConstraints());
        assertEquals(3, result.getMismatchCount());

        Map<String, Integer> severity = result.getSeverityBreakdown();
        assertEquals(1, severity.get("HIGH"));
        assertEquals(1, severity.get("MEDIUM"));
        assertEquals(1, severity.get("LOW"));

        Map<String, Integer> category = result.getCategoryBreakdown();
        assertEquals(1, category.get("MISSING_IN_API"));
        assertEquals(1, category.get("CONSTRAINT_CONFLICT"));
        assertEquals(1, category.get("UNDOCUMENTED_API"));
    }

    @Test
    void check_malformedJson_throwsMeaningfulException() {
        ClaudeClient mock = prompt -> "not valid json {{{{";
        ConsistencyChecker checker = new ConsistencyChecker(mock);

        RequirementAnalysis analysis = analysisWithConstraints(List.of(
                constraint("req-1", "Section 2.1")
        ));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> checker.check(analysis, "openapi: 3.0.0"));
        assertTrue(ex.getMessage().contains("Failed to parse ConsistencyChecker response"));
    }
}
