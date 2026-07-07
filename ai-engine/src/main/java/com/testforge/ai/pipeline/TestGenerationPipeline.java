package com.testforge.ai.pipeline;

import com.testforge.ai.cache.EndpointCache;
import com.testforge.ai.cache.FileBasedEndpointCache;
import com.testforge.ai.cache.SpecFingerprint;
import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.prompt.EndpointPromptBuilder;
import com.testforge.ai.validation.ContractViolation;
import com.testforge.ai.validation.GenerationValidationException;
import com.testforge.ai.validation.RejectedTestCase;
import com.testforge.ai.validation.StructuralValidationException;
import com.testforge.ai.validation.TestCaseContractValidator;
import com.testforge.ai.validation.TestCaseStructuralValidationResult;
import com.testforge.ai.validation.TestCaseStructuralValidator;
import com.testforge.ai.validation.ValidationIssue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

public class TestGenerationPipeline {

    private final OpenApiLoader loader;
    private final EndpointPromptBuilder promptBuilder;
    private final ClaudeClient claudeClient;
    private final ResponseParser responseParser;
    private final EndpointCache cache;
    private final TestCaseContractValidator contractValidator;
    private final TestCaseStructuralValidator structuralValidator;

    public TestGenerationPipeline(OpenApiLoader loader,
                                   EndpointPromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   ResponseParser responseParser) {
        this(loader, promptBuilder, claudeClient, responseParser, new FileBasedEndpointCache());
    }

    public TestGenerationPipeline(OpenApiLoader loader,
                                   EndpointPromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   ResponseParser responseParser,
                                   EndpointCache cache) {
        this(loader, promptBuilder, claudeClient, responseParser, cache, new TestCaseContractValidator());
    }

    public TestGenerationPipeline(OpenApiLoader loader,
                                   EndpointPromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   ResponseParser responseParser,
                                   EndpointCache cache,
                                   TestCaseContractValidator contractValidator) {
        this(loader, promptBuilder, claudeClient, responseParser, cache, contractValidator,
                new TestCaseStructuralValidator());
    }

    public TestGenerationPipeline(OpenApiLoader loader,
                                  EndpointPromptBuilder promptBuilder,
                                  ClaudeClient claudeClient,
                                  ResponseParser responseParser,
                                  EndpointCache cache,
                                  TestCaseContractValidator contractValidator,
                                  TestCaseStructuralValidator structuralValidator) {
        this.loader = loader;
        this.promptBuilder = promptBuilder;
        this.claudeClient = claudeClient;
        this.responseParser = responseParser;
        this.cache = cache;
        this.contractValidator = contractValidator;
        this.structuralValidator = structuralValidator;
    }

    public TestGenerationOutcome generate(String yamlContent) {
        List<EndpointSpec> endpoints = loader.parse(yamlContent);
        List<GenerationResult> results = new ArrayList<>();
        List<TestCase> acceptedTestCases = new ArrayList<>();
        List<RejectedTestCase> rejectedTestCases = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();

        for (EndpointSpec endpoint : endpoints) {
            String prompt = promptBuilder.build(endpoint);
            String fingerprint = SpecFingerprint.compute(prompt);
            Optional<List<TestCase>> cached = cache.findByFingerprint(fingerprint);

            List<TestCase> testCases;
            boolean cacheMiss = cached.isEmpty();
            if (cached.isPresent()) {
                System.out.println("[cache hit]  " + endpoint.getMethod() + " " + endpoint.getPath()
                        + " (fingerprint=" + fingerprint.substring(0, 8) + "...)");
                testCases = cached.get();
            } else {
                System.out.println("[cache miss → calling Claude]  " + endpoint.getMethod() + " " + endpoint.getPath()
                        + " (fingerprint=" + fingerprint.substring(0, 8) + "...)");
                String rawJson = claudeClient.generate(prompt);
                testCases = responseParser.parse(rawJson);
            }

            Map<String, Integer> originalBatchIndexById = originalBatchIndexById(testCases);
            TestCaseStructuralValidationResult structuralResult = structuralValidator.validate(testCases);
            rejectedTestCases.addAll(structuralResult.getRejectedTestCases());
            warnings.addAll(structuralResult.warnings());
            reportStructuralIssues(structuralResult);
            if (structuralResult.isBatchRejected()) {
                throw new StructuralValidationException(
                        "Generated test case batch failed structural validation", structuralResult);
            }

            testCases = structuralResult.getAcceptedTestCases();
            if (testCases.isEmpty()) {
                throw new StructuralValidationException(
                        "Generated test case batch has no structurally valid test cases", structuralResult);
            }

            List<ContractViolation> violations = contractValidator.validate(testCases, endpoint);
            if (!violations.isEmpty()) {
                ContractOutcome contractOutcome = applyContractViolations(
                        violations, originalBatchIndexById, testCases, endpoint.getPath());
                rejectedTestCases.addAll(contractOutcome.rejectedTestCases());
                if (!contractOutcome.acceptedTestCases().isEmpty()) {
                    if (cacheMiss) {
                        cache.save(fingerprint, contractOutcome.acceptedTestCases());
                    }
                    acceptedTestCases.addAll(contractOutcome.acceptedTestCases());
                    results.add(new GenerationResult(endpoint, contractOutcome.acceptedTestCases()));
                }
            } else {
                if (cacheMiss) {
                    cache.save(fingerprint, testCases);
                }
                acceptedTestCases.addAll(testCases);
                results.add(new GenerationResult(endpoint, testCases));
            }
        }

        TestGenerationOutcome outcome = new TestGenerationOutcome(results, acceptedTestCases, rejectedTestCases, warnings);
        if (acceptedTestCases.isEmpty()) {
            throw new GenerationValidationException(
                    "Generated tests were rejected by contract validation; no accepted test cases remain",
                    outcome);
        }
        return outcome;
    }

    @Deprecated
    public List<GenerationResult> run(String yamlContent) {
        TestGenerationOutcome outcome = generate(yamlContent);
        if (!outcome.getRejectedTestCases().isEmpty()) {
            throw new GenerationValidationException(
                    "Generated test cases include rejected items; use generate() for diagnostics",
                    outcome);
        }
        return outcome.getGenerationResults();
    }

    @Deprecated
    public List<ValidationIssue> getLastStructuralValidationIssues() {
        return List.of();
    }

    private void reportStructuralIssues(TestCaseStructuralValidationResult result) {
        for (ValidationIssue issue : result.getIssues()) {
            String prefix = issue.getSeverity().name().toLowerCase();
            System.out.println("[structural validation " + prefix + "] "
                    + issue.getCode() + " " + issue.getFieldPath() + ": " + issue.getMessage());
        }
    }

    private Map<String, Integer> originalBatchIndexById(List<TestCase> testCases) {
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            if (testCase != null && testCase.getId() != null && !testCase.getId().isBlank()) {
                indices.putIfAbsent(testCase.getId(), i);
            }
        }
        return indices;
    }

    private ContractOutcome applyContractViolations(List<ContractViolation> violations,
                                                    Map<String, Integer> originalBatchIndexById,
                                                    List<TestCase> testCases,
                                                    String endpointPath) {
        Map<String, List<ValidationIssue>> issuesByTestCaseId = new LinkedHashMap<>();
        for (ContractViolation violation : violations) {
            String testCaseId = violation.getTestCaseId();
            Integer index = originalBatchIndexById.get(testCaseId);
            if (index == null) {
                throw new GenerationValidationException(
                        "Contract violation referenced unknown testCaseId '" + testCaseId
                                + "' for endpoint " + endpointPath,
                        new TestGenerationOutcome(List.of(), List.of(), List.of(), List.of()));
            }

            ValidationIssue issue = ValidationIssue.error(
                    violation.getViolationType(),
                    contractFieldPath(violation.getViolationType(), index),
                    violation.getDetails());
            issuesByTestCaseId.computeIfAbsent(testCaseId, ignored -> new ArrayList<>()).add(issue);
        }

        List<RejectedTestCase> rejected = new ArrayList<>();
        for (Map.Entry<String, List<ValidationIssue>> entry : issuesByTestCaseId.entrySet()) {
            Integer index = originalBatchIndexById.get(entry.getKey());
            rejected.add(new RejectedTestCase(index, entry.getKey(), entry.getValue()));
        }

        Set<String> rejectedIds = issuesByTestCaseId.keySet();
        List<TestCase> accepted = testCases.stream()
                .filter(testCase -> !rejectedIds.contains(testCase.getId()))
                .toList();
        return new ContractOutcome(accepted, rejected);
    }

    private String contractFieldPath(String violationType, int index) {
        String base = "testCases[" + index + "]";
        if (violationType == null) {
            return base;
        }
        return switch (violationType) {
            case "PATH_NOT_FOUND" -> base + ".request.path";
            case "METHOD_MISMATCH" -> base + ".request.method";
            case "FIELD_NOT_IN_SCHEMA" -> base + ".request.body";
            case "STATUS_NOT_IN_SPEC" -> base + ".expected.status";
            default -> base;
        };
    }

    private record ContractOutcome(List<TestCase> acceptedTestCases, List<RejectedTestCase> rejectedTestCases) {
    }
}
