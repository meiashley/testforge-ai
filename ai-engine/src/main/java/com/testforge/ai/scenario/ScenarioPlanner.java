package com.testforge.ai.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.requirement.RequirementAnalysis;

import java.util.List;
import java.util.stream.Collectors;

public class ScenarioPlanner {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeClient claudeClient;

    public ScenarioPlanner(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public List<ExecutionPlan> plan(List<ResolvedFlow> flows,
                                    RequirementAnalysis requirementAnalysis,
                                    String openApiSpecContent) {
        if (flows.isEmpty()) return List.of();

        String prompt = buildPrompt(flows, requirementAnalysis, openApiSpecContent);
        String response = claudeClient.generate(prompt);
        return parsePlans(response);
    }

    private String buildPrompt(List<ResolvedFlow> flows,
                                RequirementAnalysis requirementAnalysis,
                                String spec) {
        String flowsJson;
        try {
            flowsJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(flows);
        } catch (Exception e) {
            flowsJson = flows.toString();
        }

        String constraintsJson;
        try {
            constraintsJson = MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(requirementAnalysis.getConstraints());
        } catch (Exception e) {
            constraintsJson = requirementAnalysis.getConstraints().toString();
        }

        return """
                You are an expert test scenario designer. Given resolved API flows and requirement constraints, produce executable test plans with concrete business assertions and test data.

                Your responsibilities:
                - Add testData (concrete values for ${variables}) within metadata
                - Specify requestBody content for each step (with ${var} placeholders where data flows from previous steps)
                - Add expectedStatusCode based on the scenario outcome
                - Add assertions to validate response (status, body fields, ownership rules)
                - Add stepDescription explaining the business intent of each step
                - Set scenarioId/scenarioName matching the flow

                Your NON-responsibilities (do NOT change these from the input):
                - order, pathTemplate, role, pathBindings, headerBindings, outputCapture (already resolved by ApiFlowResolver)

                For "REJECTED" scenarios (like cross-user refund, double refund):
                - The final step's expectedStatusCode should be 4xx (403, 422, etc.)
                - Assertions should verify the error code and message

                For "SUCCESS" scenarios:
                - All steps return 2xx
                - Final assertions verify successful state transition

                Output structure (JSON array, no surrounding text):
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

                Resolved flows (with ordering and bindings already specified):
                """ + flowsJson + """

                Requirement constraints:
                """ + constraintsJson + """

                OpenAPI Specification:
                """ + spec;
    }

    private List<ExecutionPlan> parsePlans(String response) {
        try {
            String cleaned = stripMarkdownFence(response);
            return MAPPER.readValue(cleaned, new TypeReference<List<ExecutionPlan>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ScenarioPlanner response: " + e.getMessage(), e);
        }
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
