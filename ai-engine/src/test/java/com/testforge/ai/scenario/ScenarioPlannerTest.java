package com.testforge.ai.scenario;

import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.requirement.RequirementAnalysis;
import com.testforge.ai.requirement.RequirementConstraint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioPlannerTest {

    private static final String ONE_PLAN_JSON = """
            [
              {
                "planId": "plan-cross-user-refund",
                "source": "scenario",
                "scenarioId": "sc-cross-user-refund",
                "scenarioName": "Cross-User Refund Attempt",
                "steps": [
                  {
                    "order": 0,
                    "stepId": "step-1",
                    "role": "creator",
                    "method": "POST",
                    "pathTemplate": "/api/payments",
                    "pathBindings": {},
                    "headerBindings": { "Authorization": "Bearer ${userA.token}" },
                    "bodyBinding": null,
                    "requestBody": "{\\"amount\\": 100, \\"currency\\": \\"AUD\\", \\"payerId\\": \\"${userA.id}\\"}",
                    "outputCapture": { "payment.id": "$.body.id" },
                    "expectedStatusCode": 201,
                    "assertions": [
                      { "path": "$.body.status", "type": "EQUALS", "expected": "COMPLETED" }
                    ],
                    "stepDescription": "User A creates a payment"
                  },
                  {
                    "order": 1,
                    "stepId": "step-2",
                    "role": "refunder",
                    "method": "POST",
                    "pathTemplate": "/api/payments/{id}/refund",
                    "pathBindings": { "id": "${payment.id}" },
                    "headerBindings": { "Authorization": "Bearer ${userB.token}" },
                    "bodyBinding": null,
                    "requestBody": null,
                    "outputCapture": {},
                    "expectedStatusCode": 403,
                    "assertions": [
                      { "path": "$.body.code", "type": "EQUALS", "expected": "FORBIDDEN_OWNERSHIP" }
                    ],
                    "stepDescription": "User B attempts to refund A's payment (should be rejected)"
                  }
                ],
                "metadata": {
                  "testData": {
                    "userA.id": "user-a-001",
                    "userA.token": "token-user-a-abc",
                    "userB.id": "user-b-002",
                    "userB.token": "token-user-b-xyz"
                  }
                }
              }
            ]
            """;

    private ResolvedFlow sampleFlow() {
        return ResolvedFlow.builder()
                .flowId("flow-cross-user-refund")
                .featureId("feat-refund-flow")
                .description("Cross-user refund attempt")
                .steps(List.of(
                        FlowStep.builder()
                                .order(0).stepId("step-1").role("creator")
                                .method("POST").pathTemplate("/api/payments")
                                .outputCapture(Map.of("payment.id", "$.body.id"))
                                .build(),
                        FlowStep.builder()
                                .order(1).stepId("step-2").role("refunder")
                                .method("POST").pathTemplate("/api/payments/{id}/refund")
                                .pathBindings(Map.of("id", "${payment.id}"))
                                .build()
                ))
                .build();
    }

    private RequirementAnalysis sampleAnalysis() {
        return RequirementAnalysis.builder()
                .constraints(List.of(
                        RequirementConstraint.builder()
                                .constraintId("req-ownership")
                                .category("OWNERSHIP")
                                .subject("payment")
                                .requirementSection("Section 4.3")
                                .build()
                ))
                .identifiedScenarios(List.of("Cross-User Refund Attempt"))
                .build();
    }

    @Test
    void plan_emptyFlows_returnsEmptyListWithoutCallingClaude() {
        ClaudeClient neverCalled = prompt -> { throw new AssertionError("Should not call Claude for empty flows"); };
        ScenarioPlanner planner = new ScenarioPlanner(neverCalled);

        List<ExecutionPlan> result = planner.plan(List.of(), sampleAnalysis(), "openapi: 3.0.0");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void plan_basicScenario_parsesAllFieldsCorrectly() {
        ClaudeClient mock = prompt -> ONE_PLAN_JSON;
        ScenarioPlanner planner = new ScenarioPlanner(mock);

        List<ExecutionPlan> result = planner.plan(List.of(sampleFlow()), sampleAnalysis(), "openapi: 3.0.0");

        assertEquals(1, result.size());

        ExecutionPlan plan = result.get(0);
        assertEquals("plan-cross-user-refund", plan.getPlanId());
        assertEquals("scenario", plan.getSource());
        assertEquals("sc-cross-user-refund", plan.getScenarioId());
        assertEquals("Cross-User Refund Attempt", plan.getScenarioName());
        assertEquals(2, plan.getSteps().size());

        ScenarioStep s0 = plan.getSteps().get(0);
        assertEquals(0, s0.getOrder());
        assertEquals("step-1", s0.getStepId());
        assertEquals("creator", s0.getRole());
        assertEquals(201, s0.getExpectedStatusCode());
        assertEquals("User A creates a payment", s0.getStepDescription());
        assertNotNull(s0.getRequestBody());
        assertEquals(1, s0.getAssertions().size());
        Assertion a0 = s0.getAssertions().get(0);
        assertEquals("$.body.status", a0.getPath());
        assertEquals("EQUALS", a0.getType());
        assertEquals("COMPLETED", a0.getExpected());

        ScenarioStep s1 = plan.getSteps().get(1);
        assertEquals(1, s1.getOrder());
        assertEquals(403, s1.getExpectedStatusCode());
        assertEquals(1, s1.getAssertions().size());
        Assertion a1 = s1.getAssertions().get(0);
        assertEquals("$.body.code", a1.getPath());
        assertEquals("FORBIDDEN_OWNERSHIP", a1.getExpected());

        assertNotNull(plan.getMetadata());
        @SuppressWarnings("unchecked")
        Map<String, Object> testData = (Map<String, Object>) plan.getMetadata().get("testData");
        assertNotNull(testData);
        assertEquals("user-a-001", testData.get("userA.id"));
        assertEquals("token-user-b-xyz", testData.get("userB.token"));
    }

    @Test
    void plan_malformedJson_throwsMeaningfulException() {
        ClaudeClient mock = prompt -> "not valid json {{{{";
        ScenarioPlanner planner = new ScenarioPlanner(mock);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> planner.plan(List.of(sampleFlow()), sampleAnalysis(), "openapi: 3.0.0"));
        assertTrue(ex.getMessage().contains("Failed to parse ScenarioPlanner response"));
    }
}
