package com.testforge.ai.requirement;

import com.testforge.ai.client.ClaudeClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequirementAnalyzerTest {

    private static final String BASIC_RESPONSE_JSON = """
            {
              "constraints": [
                {
                  "constraintId": "req-amount-range",
                  "category": "VALUE_RANGE",
                  "subject": "amount",
                  "description": "Payment amount must be between AUD 1 and AUD 100000",
                  "expectedBehavior": "Reject if outside 1-100000 with HTTP 400 INVALID_AMOUNT",
                  "requirementSection": "Section 2.1",
                  "parameters": { "min": 1, "max": 100000 }
                },
                {
                  "constraintId": "req-currency-support",
                  "category": "VALUE_RANGE",
                  "subject": "currency",
                  "description": "Supported currencies are AUD, USD, EUR",
                  "expectedBehavior": "Reject unsupported currency with HTTP 400 UNSUPPORTED_CURRENCY",
                  "requirementSection": "Section 2.2",
                  "parameters": { "supported": ["AUD", "USD", "EUR"] }
                }
              ],
              "identifiedScenarios": [
                "Happy Path: Create and Query",
                "Successful Refund Flow"
              ],
              "metadata": {
                "documentVersion": "1.0"
              }
            }
            """;

    private static final String EMPTY_RESPONSE_JSON = """
            {
              "constraints": [],
              "identifiedScenarios": [],
              "metadata": {}
            }
            """;

    @Test
    void analyze_basic_parsesConstraintsAndScenarios() {
        ClaudeClient mock = prompt -> BASIC_RESPONSE_JSON;
        RequirementAnalyzer analyzer = new RequirementAnalyzer(mock);

        RequirementAnalysis result = analyzer.analyze("# Payment Requirements\n## 2.1 Amount Constraints\n...");

        assertEquals(2, result.getConstraints().size());

        RequirementConstraint first = result.getConstraints().get(0);
        assertEquals("req-amount-range", first.getConstraintId());
        assertEquals("VALUE_RANGE", first.getCategory());
        assertEquals("amount", first.getSubject());
        assertEquals("Section 2.1", first.getRequirementSection());
        assertNotNull(first.getParameters());

        assertEquals(2, result.getIdentifiedScenarios().size());
        assertEquals("Happy Path: Create and Query", result.getIdentifiedScenarios().get(0));
        assertEquals("Successful Refund Flow", result.getIdentifiedScenarios().get(1));

        assertNotNull(result.getMetadata());
        assertEquals("1.0", result.getMetadata().get("documentVersion"));
    }

    @Test
    void analyze_emptyRequirement_returnsEmptyConstraintsAndScenarios() {
        ClaudeClient neverCalled = prompt -> { throw new AssertionError("Should not call Claude for blank input"); };
        RequirementAnalyzer analyzer = new RequirementAnalyzer(neverCalled);

        RequirementAnalysis result = analyzer.analyze("");

        assertNotNull(result);
        assertTrue(result.getConstraints().isEmpty());
        assertTrue(result.getIdentifiedScenarios().isEmpty());
    }

    @Test
    void analyze_malformedJson_throwsMeaningfulException() {
        ClaudeClient mock = prompt -> "not valid json {{{{";
        RequirementAnalyzer analyzer = new RequirementAnalyzer(mock);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> analyzer.analyze("some requirement text"));
        assertTrue(ex.getMessage().contains("Failed to parse RequirementAnalyzer response"));
    }
}
