package com.testforge.ai.requirement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.ClaudeClient;

public class RequirementAnalyzer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeClient claudeClient;

    public RequirementAnalyzer(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public RequirementAnalysis analyze(String requirementMarkdown) {
        if (requirementMarkdown == null || requirementMarkdown.isBlank()) {
            return RequirementAnalysis.builder().build();
        }
        String prompt = buildPrompt(requirementMarkdown);
        String response = claudeClient.generate(prompt);
        return parseAnalysis(response);
    }

    private String buildPrompt(String requirementMarkdown) {
        return """
                You are an expert business analyst. Analyze the following requirement document and extract structured constraints. Return ONLY a JSON object, no surrounding text.

                Output structure:
                {
                  "constraints": [
                    {
                      "constraintId": "req-amount-range",
                      "category": "VALUE_RANGE",
                      "subject": "amount",
                      "description": "Payment amount must be between AUD 1 and AUD 100,000",
                      "expectedBehavior": "Reject if outside 1-100000 with HTTP 400 INVALID_AMOUNT",
                      "requirementSection": "Section 2.1",
                      "parameters": { "min": 1, "max": 100000, "currency": "AUD" }
                    },
                    ...
                  ],
                  "identifiedScenarios": [
                    "Happy Path: Create and Query",
                    "Successful Refund Flow",
                    "Cross-User Refund Attempt",
                    ...
                  ],
                  "metadata": {
                    "documentVersion": "1.0"
                  }
                }

                Categories:
                - VALUE_RANGE: Numeric/string constraints (min/max, length, enum)
                - STATE_MACHINE: Status transitions and forbidden transitions
                - OWNERSHIP: Who can access/modify which entity
                - AUTHORIZATION: Authentication/authorization rules
                - WORKFLOW: Business process flows and idempotency

                Extract every distinct constraint. Don't merge unrelated constraints.
                Identify all scenarios listed in the "Business Scenarios for Testing" section.

                Requirement document:
                """ + requirementMarkdown;
    }

    private RequirementAnalysis parseAnalysis(String json) {
        try {
            String cleaned = stripMarkdownFence(json);
            return MAPPER.readValue(cleaned, RequirementAnalysis.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse RequirementAnalyzer response: " + e.getMessage(), e);
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
