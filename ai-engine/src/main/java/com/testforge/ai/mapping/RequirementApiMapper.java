package com.testforge.ai.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.requirement.RequirementAnalysis;

import java.util.List;
import java.util.stream.Collectors;

public class RequirementApiMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeClient claudeClient;

    public RequirementApiMapper(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public List<EndpointMapping> map(RequirementAnalysis requirementAnalysis, String openApiSpecContent) {
        if (requirementAnalysis.getConstraints().isEmpty()
                && requirementAnalysis.getIdentifiedScenarios().isEmpty()) {
            return List.of();
        }

        String prompt = buildPrompt(requirementAnalysis, openApiSpecContent);
        String response = claudeClient.generate(prompt);
        return parseMappings(response);
    }

    private String buildPrompt(RequirementAnalysis analysis, String spec) {
        String constraintsJson;
        try {
            constraintsJson = MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(analysis.getConstraints());
        } catch (Exception e) {
            constraintsJson = analysis.getConstraints().toString();
        }

        String scenariosBullets = analysis.getIdentifiedScenarios().stream()
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n"));

        return """
                You are an API specialist. Given the requirement analysis and OpenAPI spec, identify which API endpoints are involved in each feature/scenario. Return ONLY a JSON array, no surrounding text.

                Rules:
                - Identify endpoints relevant to each feature mentioned in requirements
                - Each endpoint gets a role label describing its function in that feature
                - Multiple features may share the same endpoint
                - Do not specify call order; that is handled separately

                Output structure:
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

                Common roles:
                - creator: creates a new entity
                - modifier: updates an entity
                - refunder/canceller: state-changing action
                - verifier: queries to confirm state
                - listing: retrieves a collection
                - authenticator: validates credentials

                Requirement constraints:
                """ + constraintsJson + """

                Identified scenarios:
                """ + scenariosBullets + """

                OpenAPI Specification:
                """ + spec;
    }

    private List<EndpointMapping> parseMappings(String response) {
        try {
            String cleaned = stripMarkdownFence(response);
            return MAPPER.readValue(cleaned, new TypeReference<List<EndpointMapping>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RequirementApiMapper response: " + e.getMessage(), e);
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
