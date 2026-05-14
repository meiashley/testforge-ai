package com.testforge.ai.scenario;

import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.mapping.EndpointMapping;
import com.testforge.ai.mapping.EndpointReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiFlowResolverTest {

    private static final String ONE_FLOW_JSON = """
            [
              {
                "flowId": "flow-refund",
                "featureId": "feat-refund-flow",
                "description": "Create payment, refund it, verify state",
                "steps": [
                  {
                    "order": 0,
                    "stepId": "step-1",
                    "role": "creator",
                    "method": "POST",
                    "pathTemplate": "/api/payments",
                    "pathBindings": {},
                    "headerBindings": { "Authorization": "Bearer ${user.token}" },
                    "bodyBinding": null,
                    "outputCapture": { "payment.id": "$.body.id", "payment.status": "$.body.status" }
                  },
                  {
                    "order": 1,
                    "stepId": "step-2",
                    "role": "refunder",
                    "method": "POST",
                    "pathTemplate": "/api/payments/{id}/refund",
                    "pathBindings": { "id": "${payment.id}" },
                    "headerBindings": { "Authorization": "Bearer ${user.token}" },
                    "bodyBinding": null,
                    "outputCapture": { "refund.status": "$.body.status" }
                  },
                  {
                    "order": 2,
                    "stepId": "step-3",
                    "role": "verifier",
                    "method": "GET",
                    "pathTemplate": "/api/payments/{id}",
                    "pathBindings": { "id": "${payment.id}" },
                    "headerBindings": { "Authorization": "Bearer ${user.token}" },
                    "bodyBinding": null,
                    "outputCapture": { "verified.status": "$.body.status" }
                  }
                ]
              }
            ]
            """;

    private EndpointMapping sampleMapping() {
        return EndpointMapping.builder()
                .featureId("feat-refund-flow")
                .featureName("Refund Payment")
                .requirementSection("Section 4")
                .endpoints(List.of(
                        EndpointReference.builder().method("POST").path("/api/payments").role("creator").build(),
                        EndpointReference.builder().method("POST").path("/api/payments/{id}/refund").role("refunder").build(),
                        EndpointReference.builder().method("GET").path("/api/payments/{id}").role("verifier").build()
                ))
                .build();
    }

    @Test
    void resolve_emptyMappings_returnsEmptyListWithoutCallingClaude() {
        ClaudeClient neverCalled = prompt -> { throw new AssertionError("Should not call Claude for empty mappings"); };
        ApiFlowResolver resolver = new ApiFlowResolver(neverCalled);

        List<ResolvedFlow> result = resolver.resolve(List.of(), List.of("Happy Path"), "openapi: 3.0.0");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_basicFlow_parsesFlowAndStepsCorrectly() {
        ClaudeClient mock = prompt -> ONE_FLOW_JSON;
        ApiFlowResolver resolver = new ApiFlowResolver(mock);

        List<ResolvedFlow> result = resolver.resolve(
                List.of(sampleMapping()),
                List.of("Successful Refund Flow"),
                "openapi: 3.0.0"
        );

        assertEquals(1, result.size());

        ResolvedFlow flow = result.get(0);
        assertEquals("flow-refund", flow.getFlowId());
        assertEquals("feat-refund-flow", flow.getFeatureId());
        assertNotNull(flow.getDescription());
        assertEquals(3, flow.getSteps().size());

        FlowStep s0 = flow.getSteps().get(0);
        assertEquals(0, s0.getOrder());
        assertEquals("step-1", s0.getStepId());
        assertEquals("creator", s0.getRole());
        assertEquals("POST", s0.getMethod());
        assertEquals("/api/payments", s0.getPathTemplate());
        assertTrue(s0.getPathBindings().isEmpty());
        assertEquals("Bearer ${user.token}", s0.getHeaderBindings().get("Authorization"));
        assertNull(s0.getBodyBinding());
        assertEquals("$.body.id", s0.getOutputCapture().get("payment.id"));
        assertEquals("$.body.status", s0.getOutputCapture().get("payment.status"));

        FlowStep s1 = flow.getSteps().get(1);
        assertEquals(1, s1.getOrder());
        assertEquals("step-2", s1.getStepId());
        assertEquals("refunder", s1.getRole());
        assertEquals("/api/payments/{id}/refund", s1.getPathTemplate());
        assertEquals("${payment.id}", s1.getPathBindings().get("id"));

        FlowStep s2 = flow.getSteps().get(2);
        assertEquals(2, s2.getOrder());
        assertEquals("step-3", s2.getStepId());
        assertEquals("verifier", s2.getRole());
        assertEquals("GET", s2.getMethod());
        assertEquals("$.body.status", s2.getOutputCapture().get("verified.status"));
    }

    @Test
    void resolve_malformedJson_throwsMeaningfulException() {
        ClaudeClient mock = prompt -> "not valid json {{{{";
        ApiFlowResolver resolver = new ApiFlowResolver(mock);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> resolver.resolve(List.of(sampleMapping()), List.of("some scenario"), "openapi: 3.0.0"));
        assertTrue(ex.getMessage().contains("Failed to parse ApiFlowResolver response"));
    }
}
