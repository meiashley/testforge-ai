package com.testforge.ai.consistency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.requirement.RequirementAnalysis;
import com.testforge.ai.requirement.RequirementConstraint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ConsistencyChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaudeClient claudeClient;

    public ConsistencyChecker(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public AlignmentResult check(RequirementAnalysis requirementAnalysis, String openApiSpecContent) {
        if (requirementAnalysis.getConstraints().isEmpty()) {
            return AlignmentResult.builder()
                    .mismatches(List.of())
                    .totalConstraints(0)
                    .alignedCount(0)
                    .mismatchCount(0)
                    .severityBreakdown(Map.of())
                    .categoryBreakdown(Map.of())
                    .build();
        }

        String prompt = buildPrompt(requirementAnalysis, openApiSpecContent);
        String response = claudeClient.generate(prompt);
        List<ConsistencyMismatch> mismatches = parseMismatches(response);
        return buildAlignmentResult(mismatches, requirementAnalysis);
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
                You are an expert API contract reviewer. Compare the following requirements against the OpenAPI specification and identify all mismatches. Return ONLY a JSON array, no surrounding text.

                Mismatch categories:
                - MISSING_IN_API: Requirement mandates behavior the API spec does not define
                - UNDOCUMENTED_API: API spec defines behavior the requirement does not mention
                - CONSTRAINT_CONFLICT: Both define the same field/behavior but with conflicting rules
                - AMBIGUOUS_REQUIREMENT: Requirement is unclear and cannot be implemented unambiguously
                - WORKFLOW_MISMATCH: State machine or business workflow differs between requirement and spec

                Severity guide:
                - HIGH: Critical functionality gap (e.g. ownership, idempotency, monetary limits)
                - MEDIUM: Specific constraint mismatch (e.g. type/range/format detail)
                - LOW: Documentation inconsistency (naming, descriptions)

                Confidence guide:
                - HIGH: Clear evidence in both documents
                - MEDIUM: Likely mismatch but specification is ambiguous
                - LOW: Possible mismatch, needs human verification

                Output structure:
                [
                  {
                    "mismatchId": "mm-001",
                    "category": "MISSING_IN_API",
                    "severity": "HIGH",
                    "confidence": "HIGH",
                    "summary": "Cross-user refund check absent in API spec",
                    "evidence": "Requirement Section 4.3 mandates payerId match check; OpenAPI spec for POST /api/payments/{id}/refund has no authorization rule mentioning payerId",
                    "location": "POST /api/payments/{id}/refund",
                    "requirementReference": "Section 4.3",
                    "specReference": "/api/payments/{id}/refund"
                  }
                ]

                Requirement constraints (structured):
                """ + constraintsJson + """

                Identified business scenarios:
                """ + scenariosBullets + """

                OpenAPI Specification:
                """ + spec;
    }

    private List<ConsistencyMismatch> parseMismatches(String response) {
        try {
            String cleaned = stripMarkdownFence(response);
            return MAPPER.readValue(cleaned, new TypeReference<List<ConsistencyMismatch>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ConsistencyChecker response: " + e.getMessage(), e);
        }
    }

    private AlignmentResult buildAlignmentResult(List<ConsistencyMismatch> mismatches,
                                                  RequirementAnalysis analysis) {
        int total = analysis.getConstraints().size();

        Set<String> referencedSections = mismatches.stream()
                .map(ConsistencyMismatch::getRequirementReference)
                .collect(Collectors.toSet());

        long constraintsWithMismatch = analysis.getConstraints().stream()
                .filter(c -> referencedSections.contains(c.getRequirementSection()))
                .count();

        int aligned = (int) (total - constraintsWithMismatch);

        Map<String, Integer> severityBreakdown = new HashMap<>();
        Map<String, Integer> categoryBreakdown = new HashMap<>();
        for (ConsistencyMismatch m : mismatches) {
            severityBreakdown.merge(m.getSeverity(), 1, Integer::sum);
            categoryBreakdown.merge(m.getCategory(), 1, Integer::sum);
        }

        return AlignmentResult.builder()
                .mismatches(mismatches)
                .totalConstraints(total)
                .alignedCount(aligned)
                .mismatchCount(mismatches.size())
                .severityBreakdown(severityBreakdown)
                .categoryBreakdown(categoryBreakdown)
                .build();
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
