package com.testforge.ai.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.mapping.EndpointMapping;

import java.util.List;
import java.util.stream.Collectors;

public class ApiFlowResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeClient claudeClient;

    public ApiFlowResolver(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public List<ResolvedFlow> resolve(List<EndpointMapping> mappings,
                                      List<String> identifiedScenarios,
                                      String openApiSpecContent) {
        if (mappings.isEmpty()) return List.of();

        String prompt = buildPrompt(mappings, identifiedScenarios, openApiSpecContent);
        String response = claudeClient.generate(prompt);
        return parseFlows(response);
    }

    private String buildPrompt(List<EndpointMapping> mappings,
                                List<String> identifiedScenarios,
                                String spec) {
        String mappingsJson;
        try {
            mappingsJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(mappings);
        } catch (Exception e) {
            mappingsJson = mappings.toString();
        }

        String scenariosBullets = identifiedScenarios.stream()
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n"));

        return """
                You are an API workflow architect. Given the feature-to-endpoint mappings and identified business scenarios, design an ordered execution flow for each scenario.

                Rules:
                - Each flow corresponds to one scenario from the identifiedScenarios list
                - Order endpoints logically (creator before refunder, refunder before verifier)
                - Specify data bindings using ${variable} syntax with namespaced keys
                - outputCapture uses JSONPath ($.body.id, $.headers.location, etc) to extract response data into context
                - Path/header/body bindings reference variables produced by earlier steps (or external inputs like ${user.token})
                - DO NOT include assertions or expected values; those are added by ScenarioPlanner
                - DO NOT add test data; only specify how data flows between steps

                Output structure (JSON array, no surrounding text):
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

                For cross-user scenarios, use namespaced user variables: ${userA.token}, ${userB.token}
                For test data references, use scenario-named namespaces: ${payment.id}, ${refund.id}

                Feature mappings:
                """ + mappingsJson + """

                Identified scenarios:
                """ + scenariosBullets + """

                OpenAPI Specification:
                """ + spec;
    }

    private List<ResolvedFlow> parseFlows(String response) {
        try {
            String cleaned = stripMarkdownFence(response);
            return MAPPER.readValue(cleaned, new TypeReference<List<ResolvedFlow>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ApiFlowResolver response: " + e.getMessage(), e);
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
