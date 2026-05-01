package com.testforge.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.MockClaudeClient;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.pipeline.SwaggerOpenApiLoader;
import com.testforge.ai.pipeline.TestGenerationPipeline;
import com.testforge.ai.prompt.PromptBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class V1BaselinePipelineTest {

    private static TestGenerationPipeline pipeline;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUpPipeline() {
        pipeline = new TestGenerationPipeline(
                new SwaggerOpenApiLoader(),
                new PromptBuilder(),
                new MockClaudeClient(),
                new ResponseParser()
        );
    }

    @Test
    void generatesTestCasesForAllThreeEndpoints() throws IOException {
        String yaml = TestFixtures.loadMockBankingApiSpec();

        List<GenerationResult> results = pipeline.run(yaml);

        // Print to console
        String prettyJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results);
        System.out.println("\n===== V1 BASELINE OUTPUT =====");
        System.out.println(prettyJson);
        System.out.println("==============================\n");

        // Write to file
        Path outputFile = Path.of(System.getProperty("project.basedir", "."), "target", "v1-output.json");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, prettyJson);
        System.out.println("Output written to: " + outputFile.toAbsolutePath());

        // Assert: 3 endpoints processed
        assertEquals(3, results.size(),
                "Expected 3 GenerationResults (one per endpoint), got " + results.size());

        for (GenerationResult result : results) {
            String opId = result.getEndpoint().getOperationId();

            // Assert: each endpoint has >= 3 test cases
            assertTrue(result.getTestCases().size() >= 3,
                    "Expected >= 3 TestCases for " + opId + ", got " + result.getTestCases().size());

            // Assert: required fields are non-null on every TestCase
            for (TestCase tc : result.getTestCases()) {
                assertNotNull(tc.getId(),       opId + " → tc.id must not be null");
                assertNotNull(tc.getName(),     opId + " → tc.name must not be null");
                assertNotNull(tc.getType(),     opId + " → tc.type must not be null");
                assertNotNull(tc.getScenario(), opId + " → tc.scenario must not be null");
                assertNotNull(tc.getRequest(),  opId + " → tc.request must not be null");
                assertNotNull(tc.getExpected(), opId + " → tc.expected must not be null");
            }
        }
    }
}
