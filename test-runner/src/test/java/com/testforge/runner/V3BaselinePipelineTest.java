package com.testforge.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.client.RealClaudeClient;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.pipeline.SwaggerOpenApiLoader;
import com.testforge.ai.pipeline.TestGenerationPipeline;
import com.testforge.ai.prompt.PromptBuilderV3;
import com.testforge.mockbank.MockBankingApiApplication;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.pipeline.ExecutionPipeline;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import com.testforge.runner.setup.SetupRunner;
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
class V3BaselinePipelineTest {

    @LocalServerPort
    int port;

    @Test
    void runsFullPipelineWithFixturesAndProducesReport() throws IOException {
        String baseUrl = "http://localhost:" + port;

        // 1. Build ai-engine pipeline with V3 prompt
        TestGenerationPipeline aiPipeline = new TestGenerationPipeline(
                new SwaggerOpenApiLoader(), new PromptBuilderV3(),
                new RealClaudeClient(System.getenv("ANTHROPIC_API_KEY")), new ResponseParser());

        // 3. Load openapi.yaml and generate test cases
        String yaml = TestFixtures.loadMockBankingApiSpec();
        List<GenerationResult> generationResults = aiPipeline.run(yaml);

        // 4. Execute against live server with per-test fixture initialization
        ExecutionReport report = new ExecutionPipeline(
                new HttpExecutor(), new AssertionEvaluator(),
                new ReportBuilder(), new ReportWriter("v3")
        ).run(generationResults, baseUrl, new SetupRunner());

        // 5. Assertions
        int total = report.getSummary().getTotal();
        int passed = report.getSummary().getPassed();
        int failed = report.getSummary().getFailed();

        assertTrue(total >= 9, "Expected at least 9 test cases, got: " + total);
        assertTrue(passed >= 8, "V3 target: at least 8 passed (90%+), got: " + passed);
        assertTrue(failed <= 2, "V3 target: at most 2 failures, got: " + failed);

        // 6. File artifacts
        Path outputDir = Path.of(System.getProperty("project.basedir", "."), "target");
        assertTrue(Files.exists(outputDir.resolve("v3-execution-report.json")),
                "v3-execution-report.json must exist");
        assertTrue(Files.exists(outputDir.resolve("v3-execution-report.md")),
                "v3-execution-report.md must exist");

        // JSON must parse with expected top-level structure
        assertDoesNotThrow(() -> {
            JsonNode node = new ObjectMapper().readTree(
                    outputDir.resolve("v3-execution-report.json").toFile());
            assertNotNull(node.get("summary"), "JSON must contain 'summary'");
            assertNotNull(node.get("results"), "JSON must contain 'results'");
        });
    }
}
