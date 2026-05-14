package com.testforge.ai.mapping;

import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.requirement.RequirementAnalysis;
import com.testforge.ai.requirement.RequirementConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RequirementApiMapperTest {

    private static final String TWO_MAPPINGS_JSON = """
            [
              {
                "featureId": "feat-create-payment",
                "featureName": "Create Payment",
                "featureDescription": "User initiates a new payment",
                "endpoints": [
                  {
                    "method": "POST",
                    "path": "/api/payments",
                    "role": "creator"
                  }
                ],
                "requirementSection": "Section 2"
              },
              {
                "featureId": "feat-refund-flow",
                "featureName": "Refund Payment",
                "featureDescription": "User refunds a completed payment",
                "endpoints": [
                  {
                    "method": "POST",
                    "path": "/api/payments/{id}/refund",
                    "role": "refunder"
                  },
                  {
                    "method": "GET",
                    "path": "/api/payments/{id}",
                    "role": "verifier"
                  }
                ],
                "requirementSection": "Section 4"
              }
            ]
            """;

    private RequirementAnalysis analysisWithData() {
        return RequirementAnalysis.builder()
                .constraints(List.of(
                        RequirementConstraint.builder()
                                .constraintId("req-1")
                                .category("VALUE_RANGE")
                                .subject("amount")
                                .requirementSection("Section 2.1")
                                .build()
                ))
                .identifiedScenarios(List.of("Happy Path: Create and Query", "Successful Refund Flow"))
                .build();
    }

    @Test
    void map_emptyAnalysis_returnsEmptyListWithoutCallingClaude() {
        ClaudeClient neverCalled = prompt -> { throw new AssertionError("Should not call Claude for empty analysis"); };
        RequirementApiMapper mapper = new RequirementApiMapper(neverCalled);

        RequirementAnalysis empty = RequirementAnalysis.builder().build();
        List<EndpointMapping> result = mapper.map(empty, "openapi: 3.0.0");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void map_basic_parsesMappingsCorrectly() {
        ClaudeClient mock = prompt -> TWO_MAPPINGS_JSON;
        RequirementApiMapper mapper = new RequirementApiMapper(mock);

        List<EndpointMapping> result = mapper.map(analysisWithData(), "openapi: 3.0.0");

        assertEquals(2, result.size());

        EndpointMapping first = result.get(0);
        assertEquals("feat-create-payment", first.getFeatureId());
        assertEquals("Create Payment", first.getFeatureName());
        assertEquals("Section 2", first.getRequirementSection());
        assertEquals(1, first.getEndpoints().size());
        assertEquals("POST", first.getEndpoints().get(0).getMethod());
        assertEquals("/api/payments", first.getEndpoints().get(0).getPath());
        assertEquals("creator", first.getEndpoints().get(0).getRole());

        EndpointMapping second = result.get(1);
        assertEquals("feat-refund-flow", second.getFeatureId());
        assertEquals(2, second.getEndpoints().size());
        EndpointReference refunder = second.getEndpoints().get(0);
        assertEquals("POST", refunder.getMethod());
        assertEquals("/api/payments/{id}/refund", refunder.getPath());
        assertEquals("refunder", refunder.getRole());
        EndpointReference verifier = second.getEndpoints().get(1);
        assertEquals("GET", verifier.getMethod());
        assertEquals("verifier", verifier.getRole());
    }

    @Test
    void map_malformedJson_throwsMeaningfulException() {
        ClaudeClient mock = prompt -> "not valid json {{{{";
        RequirementApiMapper mapper = new RequirementApiMapper(mock);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> mapper.map(analysisWithData(), "openapi: 3.0.0"));
        assertTrue(ex.getMessage().contains("Failed to parse RequirementApiMapper response"));
    }
}
