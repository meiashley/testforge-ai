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
import com.testforge.ai.validation.TestCaseContractValidator;

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
        this.loader = loader;
        this.promptBuilder = promptBuilder;
        this.claudeClient = claudeClient;
        this.responseParser = responseParser;
        this.cache = cache;
        this.contractValidator = contractValidator;
    }

    public List<GenerationResult> run(String yamlContent) {
        List<EndpointSpec> endpoints = loader.parse(yamlContent);
        List<GenerationResult> results = new ArrayList<>();

        for (EndpointSpec endpoint : endpoints) {
            String prompt = promptBuilder.build(endpoint);
            String fingerprint = SpecFingerprint.compute(prompt);
            Optional<List<TestCase>> cached = cache.findByFingerprint(fingerprint);

            List<TestCase> testCases;
            if (cached.isPresent()) {
                System.out.println("[cache hit]  " + endpoint.getMethod() + " " + endpoint.getPath()
                        + " (fingerprint=" + fingerprint.substring(0, 8) + "...)");
                testCases = cached.get();
            } else {
                System.out.println("[cache miss → calling Claude]  " + endpoint.getMethod() + " " + endpoint.getPath()
                        + " (fingerprint=" + fingerprint.substring(0, 8) + "...)");
                String rawJson = claudeClient.generate(prompt);
                testCases = responseParser.parse(rawJson);
                cache.save(fingerprint, testCases);
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

            results.add(new GenerationResult(endpoint, testCases));
        }

        return results;
    }
}
