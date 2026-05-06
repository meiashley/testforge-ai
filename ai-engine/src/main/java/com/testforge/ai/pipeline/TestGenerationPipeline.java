package com.testforge.ai.pipeline;

import com.testforge.ai.client.ClaudeClient;
import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.prompt.EndpointPromptBuilder;

import java.util.ArrayList;
import java.util.List;

public class TestGenerationPipeline {

    private final OpenApiLoader loader;
    private final EndpointPromptBuilder promptBuilder;
    private final ClaudeClient claudeClient;
    private final ResponseParser responseParser;

    public TestGenerationPipeline(OpenApiLoader loader,
                                   EndpointPromptBuilder promptBuilder,
                                   ClaudeClient claudeClient,
                                   ResponseParser responseParser) {
        this.loader = loader;
        this.promptBuilder = promptBuilder;
        this.claudeClient = claudeClient;
        this.responseParser = responseParser;
    }

    public List<GenerationResult> run(String yamlContent) {
        List<EndpointSpec> endpoints = loader.parse(yamlContent);
        List<GenerationResult> results = new ArrayList<>();

        for (EndpointSpec endpoint : endpoints) {
            String prompt = promptBuilder.build(endpoint);
            String rawJson = claudeClient.generate(prompt);
            List<TestCase> testCases = responseParser.parse(rawJson);
            results.add(new GenerationResult(endpoint, testCases));
        }

        return results;
    }
}
