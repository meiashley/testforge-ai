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
import com.testforge.ai.validation.RejectedTestCase;
import com.testforge.ai.validation.StructuralValidationException;
import com.testforge.ai.validation.TestCaseContractValidator;
import com.testforge.ai.validation.TestCaseStructuralValidationResult;
import com.testforge.ai.validation.TestCaseStructuralValidator;
import com.testforge.ai.validation.ValidationIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                Set<String> violatingIds = new java.util.HashSet<>();
                for (ContractViolation v : violations) {
                    System.out.println("[contract violation] " + v.getTestCaseName() + ": " + v.getDetails());
                    violatingIds.add(v.getTestCaseId());
                }
                testCases = testCases.stream()
                        .filter(tc -> !violatingIds.contains(tc.getId()))
                        .toList();
            }

            if (cacheMiss) {
                cache.save(fingerprint, testCases);
            }

            acceptedTestCases.addAll(testCases);
            results.add(new GenerationResult(endpoint, testCases));
        }

        return new TestGenerationOutcome(results, acceptedTestCases, rejectedTestCases, warnings);
    }

    @Deprecated
    public List<GenerationResult> run(String yamlContent) {
        TestGenerationOutcome outcome = generate(yamlContent);
        if (!outcome.getRejectedTestCases().isEmpty()) {
            throw new StructuralValidationException(
                    "Generated test cases include rejected items; use generate() for structural diagnostics",
                    new TestCaseStructuralValidationResult(
                            outcome.getAcceptedTestCases(),
                            outcome.getRejectedTestCases().stream()
                                    .flatMap(rejected -> rejected.getValidationIssues().stream())
                                    .toList(),
                            false,
                            outcome.getRejectedTestCases()));
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
}
