package com.testforge.runner;

import com.testforge.ai.analysis.FailureAnalysisInput;
import com.testforge.ai.analysis.FailureAnalyzer;
import com.testforge.ai.client.RealClaudeClient;
import com.testforge.ai.consistency.ConsistencyChecker;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.mapping.RequirementApiMapper;
import com.testforge.ai.orchestrator.QualityContext;
import com.testforge.ai.orchestrator.QualityPipelineOrchestrator;
import com.testforge.ai.parser.ResponseParser;
import com.testforge.ai.pipeline.SwaggerOpenApiLoader;
import com.testforge.ai.pipeline.TestGenerationPipeline;
import com.testforge.ai.prompt.PromptBuilderV4;
import com.testforge.ai.requirement.RequirementAnalyzer;
import com.testforge.ai.scenario.ApiFlowResolver;
import com.testforge.ai.scenario.ExecutionPlan;
import com.testforge.ai.scenario.ScenarioPlanner;
import com.testforge.ai.validation.TestCaseContractValidator;
import com.testforge.mockbank.MockBankingApiApplication;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.execution.StepResult;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import com.testforge.runner.model.TestCaseResult;
import com.testforge.runner.model.TestResultStatus;
import com.testforge.runner.pipeline.ExecutionPipeline;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import com.testforge.runner.setup.SetupRunner;
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

        // 5. Reuse V4 API-layer generation + execution against the same live server
        TestGenerationPipeline aiPipeline = new TestGenerationPipeline(
                new SwaggerOpenApiLoader(),
                new PromptBuilderV4(),
                claude,
                new ResponseParser(),
                new com.testforge.ai.cache.FileBasedEndpointCache(),
                new TestCaseContractValidator()
        );
        List<GenerationResult> apiGenResults = aiPipeline.run(specContent);

        ExecutionPipeline apiExecutionPipeline = new ExecutionPipeline(
                new HttpExecutor(),
                new AssertionEvaluator(),
                new ReportBuilder(),
                new ReportWriter("v5-api")
        );
        ExecutionReport apiReport = apiExecutionPipeline.run(apiGenResults, baseUrl, new SetupRunner());
        List<TestCaseResult> apiResults = apiReport.getResults();

        int apiPassed = (int) apiResults.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
        int apiFailed = (int) apiResults.stream().filter(r -> r.getStatus() == TestResultStatus.FAILED).count();
        System.out.println("[V5] api tests executed    : " + apiResults.size());
        System.out.println("[V5] api tests passed      : " + apiPassed);
        System.out.println("[V5] api tests failed      : " + apiFailed);

        // 6. Execute each scenario plan against the live API
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

        // 7. Collect API + scenario failures → diagnose with AI
        List<FailureAnalysisInput> failures = collectFailures(apiResults, planResults);
        if (!failures.isEmpty()) {
            orchestrator.diagnoseFailures(context, failures);
            System.out.println("[V5] failures diagnosed    : " + context.getFailureAnalysis().size());
        }

        // 8. Build unified ExecutionReport
        long totalMs = apiResults.stream().mapToLong(TestCaseResult::getDurationMs).sum()
                + planResults.stream()
                .flatMap(r -> r.getSteps().stream())
                .mapToLong(sr -> sr.getResponse() != null ? sr.getResponse().getDurationMs() : 0L)
                .sum();

        ExecutionSummary summary = new ExecutionSummary(
                apiResults.size(),
                apiPassed,
                apiFailed,
                apiReport.getSummary().getErrored(),
                apiResults.isEmpty() ? 0.0 : (double) apiPassed / apiResults.size(),
                Instant.now().toString(),
                totalMs
        );

        ExecutionReport report = new ExecutionReport(summary, apiResults);
        report.setScenarioResults(planResults);
        report.setConsistencyResult(context.getConsistencyResult());
        report.setRequirementAnalysis(context.getRequirementAnalysis());
        if (context.getFailureAnalysis() != null) {
            report.setFailureAnalysis(context.getFailureAnalysis());
        }

        // 9. Write reports (JSON + MD + HTML via ReportWriter)
        Path outputDir = Path.of(basedir, "target");
        new ReportWriter("v5").write(report, outputDir);

        System.out.println("[V5] report written to     : " + outputDir.resolve("v5-execution-report.html"));

        // 10. Assertions
        assertTrue(context.getConsistencyResult().getMismatchCount() > 0,
                "payment-system-requirements.md is intentionally richer than the mock API spec — expect mismatches");
        assertFalse(apiResults.isEmpty(), "at least one API test must have been executed");
        assertFalse(planResults.isEmpty(), "at least one scenario plan must have been executed");

        Path htmlReport = outputDir.resolve("v5-execution-report.html");
        assertTrue(Files.exists(htmlReport), "v5-execution-report.html must exist");
        assertTrue(Files.exists(outputDir.resolve("v5-execution-report.json")), "v5-execution-report.json must exist");
        assertTrue(Files.exists(outputDir.resolve("v5-execution-report.md")), "v5-execution-report.md must exist");

        String html = Files.readString(htmlReport);
        assertTrue(html.contains("TestForge AI Execution Report"), "HTML must contain report title");
        assertTrue(html.contains("Scenarios") || html.contains("scenario"), "HTML must contain scenario section");
    }

    private List<FailureAnalysisInput> collectFailures(List<TestCaseResult> apiResults,
                                                       List<PlanExecutionResult> planResults) {
        List<FailureAnalysisInput> inputs = new ArrayList<>();
        for (TestCaseResult apiResult : apiResults) {
            if (apiResult.getStatus() != TestResultStatus.PASSED) {
                inputs.add(buildFailureInputFromTestResult(apiResult));
            }
        }
        inputs.addAll(collectFailedSteps(planResults));
        return inputs;
    }

    private List<FailureAnalysisInput> collectFailedSteps(List<PlanExecutionResult> planResults) {
        List<FailureAnalysisInput> inputs = new ArrayList<>();
        for (PlanExecutionResult plan : planResults) {
            if (plan.getSteps() == null) continue;
            for (StepResult sr : plan.getSteps()) {
                // Skip: passed, or skipped (no response to analyze)
                if (sr.isPassed() || sr.getSkipReason() != null || sr.getResponse() == null) continue;
                inputs.add(buildFailureInputFromStepResult(plan, sr));
            }
        }
        return inputs;
    }

    private FailureAnalysisInput buildFailureInputFromTestResult(TestCaseResult r) {
        String requestBody = r.getRequest().getBody() != null ? String.valueOf(r.getRequest().getBody()) : "";
        String actualResponseBody = r.getHttpResponse() != null && r.getHttpResponse().getRawBody() != null
                ? r.getHttpResponse().getRawBody()
                : "";
        String assertionFailures = r.getAssertionResults() == null ? "" : r.getAssertionResults().stream()
                .filter(ar -> !ar.isPassed())
                .map(ar -> "field '" + ar.getField() + "': " + ar.getFailureReason())
                .collect(Collectors.joining("; "));

        return FailureAnalysisInput.builder()
                .testCaseId(r.getTestCaseId())
                .testCaseName(r.getName())
                .testCaseType(r.getType() != null ? r.getType().name() : "UNKNOWN")
                .endpointMethod(r.getRequest().getMethod())
                .endpointPath(r.getRequest().getPath())
                .requestBody(requestBody)
                .expectedStatusCode(0)
                .actualStatusCode(r.getHttpResponse() != null ? r.getHttpResponse().getStatusCode() : 0)
                .actualResponseBody(actualResponseBody)
                .assertionFailures(assertionFailures)
                .build();
    }

    private FailureAnalysisInput buildFailureInputFromStepResult(PlanExecutionResult plan, StepResult sr) {
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
        return FailureAnalysisInput.builder()
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
                .build();
    }
}
