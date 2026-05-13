package com.testforge.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.RealClaudeClient;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.pipeline.SwaggerOpenApiLoader;
import com.testforge.ai.pipeline.TestGenerationPipeline;
import com.testforge.ai.prompt.PromptBuilderV2;
import com.testforge.mockbank.MockBankingApiApplication;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.pipeline.ExecutionPipeline;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@SpringBootTest(
        classes = MockBankingApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class V2BaselinePipelineTest {

    @LocalServerPort
    int port;

    @Test
    void runsFullPipelineAndProducesReport() throws IOException {
        // 1. Build ai-engine pipeline with V2 prompt
        TestGenerationPipeline aiPipeline = new TestGenerationPipeline(
                new SwaggerOpenApiLoader(), new PromptBuilderV2(),
                new RealClaudeClient(System.getenv("ANTHROPIC_API_KEY")), new ResponseParser());

        // 2. Load openapi.yaml and generate test cases
        String yaml = TestFixtures.loadMockBankingApiSpec();
        List<GenerationResult> generationResults = aiPipeline.run(yaml);

        // 3. Execute against live server
        String baseUrl = "http://localhost:" + port;
        ExecutionReport report = new ExecutionPipeline(
                new HttpExecutor(), new AssertionEvaluator(),
                new ReportBuilder(), new ReportWriter("v2")
        ).run(generationResults, baseUrl);

        // 4. Assertions
        int total = report.getSummary().getTotal();
        assertTrue(total >= 9, "Expected at least 9 test cases, got: " + total);
        assertTrue(report.getSummary().getPassed() >= 1, "at least 1 test must PASS");
        assertTrue(report.getSummary().getFailed() >= 1, "at least 1 test must FAIL (V2 baseline)");

        // 5. File artifacts
        Path outputDir = Path.of(System.getProperty("project.basedir", "."), "target");
        assertTrue(Files.exists(outputDir.resolve("v2-execution-report.json")),
                "v2-execution-report.json must exist");
        assertTrue(Files.exists(outputDir.resolve("v2-execution-report.md")),
                "v2-execution-report.md must exist");

        // JSON must parse as valid JSON with expected top-level structure
        assertDoesNotThrow(() -> {
            JsonNode node = new ObjectMapper().readTree(
                    outputDir.resolve("v2-execution-report.json").toFile());
            assertNotNull(node.get("summary"), "JSON must contain 'summary'");
            assertNotNull(node.get("results"), "JSON must contain 'results'");
        });
    }
}
