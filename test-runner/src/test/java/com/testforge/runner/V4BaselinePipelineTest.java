package com.testforge.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.analysis.FailureAnalyzer;
import com.testforge.ai.client.RealClaudeClient;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.pipeline.SwaggerOpenApiLoader;
import com.testforge.ai.pipeline.TestGenerationPipeline;
import com.testforge.ai.prompt.PromptBuilderV4;
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
class V4BaselinePipelineTest {

    @LocalServerPort
    int port;

    @Test
    void runsFullPipelineWithFixturesAndProducesReport() throws IOException {
        String baseUrl = "http://localhost:" + port;
        RealClaudeClient claudeClient = new RealClaudeClient(System.getenv("ANTHROPIC_API_KEY"));

        // 1. Build ai-engine pipeline with V4 dimension-driven prompt
        TestGenerationPipeline aiPipeline = new TestGenerationPipeline(
                new SwaggerOpenApiLoader(), new PromptBuilderV4(),
                claudeClient, new ResponseParser());

        // 2. Load openapi.yaml and generate test cases
        String yaml = TestFixtures.loadMockBankingApiSpec();
        List<GenerationResult> generationResults = aiPipeline.run(yaml);

        // 3. Build failure analyzer backed by same Claude client
        FailureAnalyzer analyzer = new FailureAnalyzer(claudeClient);

        // 4. Execute against live server with per-test fixture initialization
        ExecutionReport report = new ExecutionPipeline(
                new HttpExecutor(), new AssertionEvaluator(),
                new ReportBuilder(), new ReportWriter("v4")
        ).withFailureAnalyzer(analyzer)
         .run(generationResults, baseUrl, new SetupRunner());

        // 5. Execution assertions
        int total = report.getSummary().getTotal();
        int passed = report.getSummary().getPassed();
        int failed = report.getSummary().getFailed();

        assertTrue(total >= 6, "V4 dimension-driven: expected at least 6 test cases, got: " + total);
        assertTrue(passed >= total * 0.6, "V4 target: at least 60% pass, got: " + passed + "/" + total);

        // 6. Failure analysis assertions
        if (failed > 0) {
            assertNotNull(report.getFailureAnalysis(), "failureAnalysis must not be null when there are failures");
            assertEquals(failed, report.getFailureAnalysis().size(),
                    "failureAnalysis size must equal number of failed test cases");
            assertTrue(
                    report.getFailureAnalysis().stream()
                            .anyMatch(a -> a.getRootCauseCategory() != null && !a.getRootCauseCategory().isBlank()),
                    "At least one FailureAnalysisResult must have a non-blank rootCauseCategory"
            );
        }

        // 7. File artifacts
        Path outputDir = Path.of(System.getProperty("project.basedir", "."), "target");
        assertTrue(Files.exists(outputDir.resolve("v4-execution-report.json")),
                "v4-execution-report.json must exist");
        assertTrue(Files.exists(outputDir.resolve("v4-execution-report.md")),
                "v4-execution-report.md must exist");

        // JSON must parse with expected top-level structure
        assertDoesNotThrow(() -> {
            JsonNode node = new ObjectMapper().readTree(
                    outputDir.resolve("v4-execution-report.json").toFile());
            assertNotNull(node.get("summary"), "JSON must contain 'summary'");
            assertNotNull(node.get("results"), "JSON must contain 'results'");
            assertNotNull(node.get("failureAnalysis"), "JSON must contain 'failureAnalysis'");
        });
    }
}
