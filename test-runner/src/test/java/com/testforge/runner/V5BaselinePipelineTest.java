package com.testforge.runner;

import com.testforge.ai.analysis.FailureAnalysisInput;
import com.testforge.ai.analysis.FailureAnalyzer;
import com.testforge.ai.client.RealClaudeClient;
import com.testforge.ai.consistency.ConsistencyChecker;
import com.testforge.ai.mapping.RequirementApiMapper;
import com.testforge.ai.orchestrator.QualityContext;
import com.testforge.ai.orchestrator.QualityPipelineOrchestrator;
import com.testforge.ai.requirement.RequirementAnalyzer;
import com.testforge.ai.scenario.ApiFlowResolver;
import com.testforge.ai.scenario.ExecutionPlan;
import com.testforge.ai.scenario.ScenarioPlanner;
import com.testforge.mockbank.MockBankingApiApplication;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.execution.StepResult;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import com.testforge.runner.pipeline.ExecutionPipeline;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@SpringBootTest(
        classes = MockBankingApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class V5BaselinePipelineTest {

    @LocalServerPort
    int port;

    @Test
    void runsFullV5PipelineAndProducesUnifiedReport() throws Exception {
        String basedir = System.getProperty("project.basedir", ".");
        String baseUrl = "http://localhost:" + port;

        // 1. Load OpenAPI spec + requirement document
        String specContent = TestFixtures.loadMockBankingApiSpec();
        Path requirementPath = Path.of(basedir)
                .resolve("../docs/requirements/payment-system-requirements.md")
                .normalize();
        String requirementContent = Files.readString(requirementPath);

        // 2. Build all AI services
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        RealClaudeClient claude = new RealClaudeClient(apiKey);

        QualityPipelineOrchestrator orchestrator = new QualityPipelineOrchestrator(
                new RequirementAnalyzer(claude),
                new ConsistencyChecker(claude),
                new RequirementApiMapper(claude),
                new ApiFlowResolver(claude),
                new ScenarioPlanner(claude),
                new FailureAnalyzer(claude)
        );

        // 3. Build context
        QualityContext context = QualityContext.builder()
                .openApiSpecContent(specContent)
                .requirementContent(requirementContent)
                .build();

        // 4. Run AI analysis + planning (5 Claude calls)
        orchestrator.analyzeAndPlan(context);

        // Verify each stage produced output
        assertNotNull(context.getRequirementAnalysis(), "requirementAnalysis must not be null");
        assertFalse(context.getRequirementAnalysis().getConstraints().isEmpty(),
                "constraints must not be empty");
        assertFalse(context.getRequirementAnalysis().getIdentifiedScenarios().isEmpty(),
                "identifiedScenarios must not be empty");
        assertNotNull(context.getConsistencyResult(), "consistencyResult must not be null");
        assertFalse(context.getMappings().isEmpty(), "mappings must not be empty");
        assertFalse(context.getResolvedFlows().isEmpty(), "resolvedFlows must not be empty");
        assertFalse(context.getPlans().isEmpty(), "plans must not be empty");

        System.out.println("[V5] constraints extracted : " + context.getRequirementAnalysis().getConstraints().size());
        System.out.println("[V5] scenarios identified  : " + context.getRequirementAnalysis().getIdentifiedScenarios().size());
        System.out.println("[V5] consistency mismatches: " + context.getConsistencyResult().getMismatchCount());
        System.out.println("[V5] endpoint mappings     : " + context.getMappings().size());
        System.out.println("[V5] resolved flows        : " + context.getResolvedFlows().size());
        System.out.println("[V5] execution plans       : " + context.getPlans().size());

        // 5. Execute each plan against the live API
        ExecutionPipeline pipeline = new ExecutionPipeline(
                new HttpExecutor(),
                new AssertionEvaluator(),
                new ReportBuilder(),
                new ReportWriter("v5")
        );

        List<PlanExecutionResult> planResults = new ArrayList<>();
        for (ExecutionPlan plan : context.getPlans()) {
            PlanExecutionResult result = pipeline.executePlan(plan, baseUrl);
            planResults.add(result);
        }

        long scenariosPassed = planResults.stream().filter(PlanExecutionResult::isPassed).count();
        long scenariosFailed = planResults.size() - scenariosPassed;
        System.out.println("[V5] scenarios executed    : " + planResults.size());
        System.out.println("[V5] scenarios passed      : " + scenariosPassed);
        System.out.println("[V5] scenarios failed      : " + scenariosFailed);

        // 6. Collect failed steps → diagnose with AI
        List<FailureAnalysisInput> failures = collectFailedSteps(planResults);
        if (!failures.isEmpty()) {
            orchestrator.diagnoseFailures(context, failures);
            System.out.println("[V5] failures diagnosed    : " + context.getFailureAnalysis().size());
        }

        // 7. Build unified ExecutionReport
        long totalMs = planResults.stream()
                .flatMap(r -> r.getSteps().stream())
                .mapToLong(sr -> sr.getResponse() != null ? sr.getResponse().getDurationMs() : 0L)
                .sum();

        ExecutionSummary summary = new ExecutionSummary(
                planResults.size(),
                (int) scenariosPassed,
                (int) scenariosFailed,
                0,
                planResults.isEmpty() ? 0.0 : (double) scenariosPassed / planResults.size(),
                Instant.now().toString(),
                totalMs
        );

        ExecutionReport report = new ExecutionReport(summary, List.of());
        report.setScenarioResults(planResults);
        report.setConsistencyResult(context.getConsistencyResult());
        report.setRequirementAnalysis(context.getRequirementAnalysis());
        if (context.getFailureAnalysis() != null) {
            report.setFailureAnalysis(context.getFailureAnalysis());
        }

        // 8. Write reports (JSON + MD + HTML via ReportWriter)
        Path outputDir = Path.of(basedir, "target");
        new ReportWriter("v5").write(report, outputDir);

        System.out.println("[V5] report written to     : " + outputDir.resolve("v5-execution-report.html"));

        // 9. Assertions
        assertTrue(context.getConsistencyResult().getMismatchCount() > 0,
                "payment-system-requirements.md is intentionally richer than the mock API spec — expect mismatches");
        assertFalse(planResults.isEmpty(), "at least one scenario plan must have been executed");

        Path htmlReport = outputDir.resolve("v5-execution-report.html");
        assertTrue(Files.exists(htmlReport), "v5-execution-report.html must exist");
        assertTrue(Files.exists(outputDir.resolve("v5-execution-report.json")), "v5-execution-report.json must exist");
        assertTrue(Files.exists(outputDir.resolve("v5-execution-report.md")), "v5-execution-report.md must exist");

        String html = Files.readString(htmlReport);
        assertTrue(html.contains("TestForge AI Execution Report"), "HTML must contain report title");
        assertTrue(html.contains("Scenarios") || html.contains("scenario"), "HTML must contain scenario section");
    }

    private List<FailureAnalysisInput> collectFailedSteps(List<PlanExecutionResult> planResults) {
        List<FailureAnalysisInput> inputs = new ArrayList<>();
        for (PlanExecutionResult plan : planResults) {
            if (plan.getSteps() == null) continue;
            for (StepResult sr : plan.getSteps()) {
                // Skip: passed, or skipped (no response to analyze)
                if (sr.isPassed() || sr.getSkipReason() != null || sr.getResponse() == null) continue;

                String assertionFailures = "";
                if (sr.getAssertionResults() != null) {
                    assertionFailures = sr.getAssertionResults().stream()
                            .filter(ar -> !ar.isPassed())
                            .map(ar -> {
                                String path = ar.getAssertion() != null ? ar.getAssertion().getPath() : "?";
                                String expected = ar.getAssertion() != null
                                        ? String.valueOf(ar.getAssertion().getExpected()) : "?";
                                return path + ": expected " + expected + " but was " + ar.getActualValue();
                            })
                            .collect(Collectors.joining("; "));
                }

                String rawBody = sr.getResponse().getRawBody();
                inputs.add(FailureAnalysisInput.builder()
                        .testCaseId(plan.getPlanId() + "-" + sr.getStep().getStepId())
                        .testCaseName(plan.getScenarioName() + " | " + sr.getStep().getRole())
                        .testCaseType("SCENARIO")
                        .endpointMethod(sr.getStep().getMethod())
                        .endpointPath(sr.getStep().getPathTemplate())
                        .requestBody(sr.getStep().getRequestBody() != null ? sr.getStep().getRequestBody() : "")
                        .expectedStatusCode(sr.getStep().getExpectedStatusCode())
                        .actualStatusCode(sr.getResponse().getStatusCode())
                        .actualResponseBody(rawBody != null ? rawBody : "")
                        .assertionFailures(assertionFailures)
                        .build());
            }
        }
        return inputs;
    }
}
