package com.testforge.runner.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.analysis.FailureAnalysisInput;
import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.ai.analysis.FailureAnalyzer;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.scenario.ExecutionPlan;
import com.testforge.ai.scenario.ScenarioStep;
import com.testforge.ai.scenario.StepDataContext;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.execution.BindingResolver;
import com.testforge.runner.execution.JsonPathExtractor;
import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.execution.StepResult;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.*;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import com.testforge.runner.setup.SetupRunner;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExecutionPipeline {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpExecutor httpExecutor;
    private final AssertionEvaluator assertionEvaluator;
    private final ReportBuilder reportBuilder;
    private final ReportWriter reportWriter;
    private final BindingResolver bindingResolver;
    private FailureAnalyzer failureAnalyzer;

    public ExecutionPipeline(HttpExecutor httpExecutor, AssertionEvaluator assertionEvaluator,
                             ReportBuilder reportBuilder, ReportWriter reportWriter) {
        this.httpExecutor = httpExecutor;
        this.assertionEvaluator = assertionEvaluator;
        this.reportBuilder = reportBuilder;
        this.reportWriter = reportWriter;
        this.bindingResolver = new BindingResolver();
    }

    public ExecutionPipeline withFailureAnalyzer(FailureAnalyzer analyzer) {
        this.failureAnalyzer = analyzer;
        return this;
    }

    public ExecutionReport run(List<GenerationResult> generationResults, String baseUrl) {
        return run(generationResults, baseUrl, Map.of());
    }

    public ExecutionReport run(List<GenerationResult> generationResults, String baseUrl, Map<String, String> fixtures) {
        List<TestCaseResult> results = new ArrayList<>();

        for (GenerationResult generation : generationResults) {
            for (TestCase testCase : generation.getTestCases()) {
                applyFixtures(testCase, fixtures);
                results.add(execute(testCase, baseUrl));
            }
        }

        ExecutionReport report = reportBuilder.build(results);
        analyzeFailures(report);
        Path outputDir = Path.of(System.getProperty("project.basedir", "."), "target");
        reportWriter.write(report, outputDir);
        return report;
    }

    public ExecutionReport run(List<GenerationResult> generationResults, String baseUrl, SetupRunner setupRunner) {
        List<TestCaseResult> results = new ArrayList<>();

        for (GenerationResult generation : generationResults) {
            for (TestCase testCase : generation.getTestCases()) {
                Map<String, String> fixtures = hasPlaceholders(testCase)
                        ? setupRunner.run(baseUrl)
                        : Map.of();
                applyFixtures(testCase, fixtures);
                results.add(execute(testCase, baseUrl));
            }
        }

        ExecutionReport report = reportBuilder.build(results);
        analyzeFailures(report);
        Path outputDir = Path.of(System.getProperty("project.basedir", "."), "target");
        reportWriter.write(report, outputDir);
        return report;
    }

    // === V5: multi-step ExecutionPlan support ===

    public PlanExecutionResult executePlan(ExecutionPlan plan, String baseUrl) {
        StepDataContext context = new StepDataContext();

        if (plan.getMetadata() != null && plan.getMetadata().get("testData") instanceof Map<?, ?> testData) {
            testData.forEach((k, v) -> context.put(String.valueOf(k), v));
        }

        List<StepResult> stepResults = new ArrayList<>();
        boolean previousFailed = false;

        for (ScenarioStep step : plan.getSteps()) {
            if (previousFailed) {
                stepResults.add(StepResult.skipped(step));
                continue;
            }

            StepResult result = executeStep(step, context, baseUrl);
            stepResults.add(result);

            if (!result.isPassed()) {
                previousFailed = true;
            } else {
                captureOutputs(step, result.getResponse(), context);
            }
        }

        return PlanExecutionResult.builder()
                .planId(plan.getPlanId())
                .scenarioId(plan.getScenarioId())
                .scenarioName(plan.getScenarioName())
                .steps(stepResults)
                .passed(stepResults.stream().allMatch(StepResult::isPassed))
                .build();
    }

    private StepResult executeStep(ScenarioStep step, StepDataContext context, String baseUrl) {
        String resolvedPath = step.getPathTemplate();
        if (step.getPathBindings() != null) {
            for (Map.Entry<String, String> binding : step.getPathBindings().entrySet()) {
                String resolvedValue = bindingResolver.resolve(binding.getValue(), context);
                resolvedPath = resolvedPath.replace("{" + binding.getKey() + "}", resolvedValue);
            }
        }

        Map<String, String> headers = bindingResolver.resolveMap(step.getHeaderBindings(), context);

        String body = step.getRequestBody() != null
                ? bindingResolver.resolve(step.getRequestBody(), context)
                : null;

        HttpResponse response = httpExecutor.execute(step.getMethod(), baseUrl + resolvedPath, headers, body);

        boolean statusOk = response.getStatusCode() == step.getExpectedStatusCode();
        List<com.testforge.runner.execution.AssertionResult> assertionResults =
                assertionEvaluator.evaluateV5(step.getAssertions(), response);
        boolean allAssertionsPass = assertionResults.stream()
                .allMatch(com.testforge.runner.execution.AssertionResult::isPassed);

        return StepResult.builder()
                .step(step)
                .response(response)
                .statusMatch(statusOk)
                .assertionResults(assertionResults)
                .passed(statusOk && allAssertionsPass)
                .build();
    }

    private void captureOutputs(ScenarioStep step, HttpResponse response, StepDataContext context) {
        if (step.getOutputCapture() == null) return;
        for (Map.Entry<String, String> entry : step.getOutputCapture().entrySet()) {
            Object captured = JsonPathExtractor.extract(response, entry.getValue());
            context.put(entry.getKey(), captured);
        }
    }

    private void analyzeFailures(ExecutionReport report) {
        if (failureAnalyzer == null) return;

        List<TestCaseResult> failedResults = report.getResults().stream()
                .filter(r -> r.getStatus() == TestResultStatus.FAILED)
                .toList();

        if (failedResults.isEmpty()) return;

        List<FailureAnalysisInput> inputs = failedResults.stream()
                .map(this::toFailureAnalysisInput)
                .toList();

        try {
            List<FailureAnalysisResult> analyses = failureAnalyzer.analyze(inputs);
            report.setFailureAnalysis(analyses);
            System.out.printf("[failure analysis] analyzed %d failures, returned %d diagnoses%n",
                    inputs.size(), analyses.size());
        } catch (Exception e) {
            System.err.printf("[failure analysis] failed to analyze failures: %s%n", e.getMessage());
        }
    }

    private FailureAnalysisInput toFailureAnalysisInput(TestCaseResult r) {
        String requestBody = "";
        try {
            requestBody = MAPPER.writeValueAsString(r.getRequest().getBody());
        } catch (Exception ignored) {}

        String actualResponseBody = "";
        try {
            actualResponseBody = r.getHttpResponse() != null
                    ? MAPPER.writeValueAsString(r.getHttpResponse().getBody())
                    : "";
        } catch (Exception ignored) {}

        String assertionFailures = r.getAssertionResults().stream()
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
                .actualStatusCode(r.getHttpResponse() != null ? r.getHttpResponse().getStatusCode() : 0)
                .actualResponseBody(actualResponseBody)
                .expectedStatusCode(0)
                .assertionFailures(assertionFailures)
                .build();
    }

    private boolean hasPlaceholders(TestCase testCase) {
        if (testCase.getRequest().getPath() != null && testCase.getRequest().getPath().contains("{{")) return true;
        if (testCase.getRequest().getBody() != null) {
            for (Object val : testCase.getRequest().getBody().values()) {
                if (val instanceof String s && s.contains("{{")) return true;
            }
        }
        if (testCase.getExpected().getBodyAssertions() != null) {
            for (Object val : testCase.getExpected().getBodyAssertions().values()) {
                if (val instanceof String s && s.contains("{{")) return true;
            }
        }
        return false;
    }

    private void applyFixtures(TestCase testCase, Map<String, String> fixtures) {
        if (fixtures.isEmpty()) return;

        if (testCase.getRequest().getPath() != null) {
            testCase.getRequest().setPath(substitute(testCase.getRequest().getPath(), fixtures));
        }

        if (testCase.getRequest().getBody() != null) {
            testCase.getRequest().setBody(substituteMap(testCase.getRequest().getBody(), fixtures));
        }

        if (testCase.getExpected().getBodyAssertions() != null) {
            testCase.getExpected().setBodyAssertions(substituteMap(testCase.getExpected().getBodyAssertions(), fixtures));
        }
    }

    private String substitute(String value, Map<String, String> fixtures) {
        for (Map.Entry<String, String> entry : fixtures.entrySet()) {
            value = value.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return value;
    }

    private Map<String, Object> substituteMap(Map<String, Object> map, Map<String, String> fixtures) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof String s) {
                result.put(entry.getKey(), substitute(s, fixtures));
            } else {
                result.put(entry.getKey(), val);
            }
        }
        return result;
    }

    private TestCaseResult execute(TestCase testCase, String baseUrl) {
        long start = System.currentTimeMillis();
        try {
            HttpResponse httpResponse = httpExecutor.execute(testCase.getRequest(), baseUrl);
            long durationMs = System.currentTimeMillis() - start;

            int expectedStatus = testCase.getExpected().getStatus();
            boolean statusMatches = httpResponse.getStatusCode() == expectedStatus;

            List<AssertionResult> assertionResults = assertionEvaluator.evaluate(
                    testCase.getExpected().getBodyAssertions(),
                    httpResponse.getBody());

            boolean allBodyAssertionsPassed = assertionResults.stream().allMatch(AssertionResult::isPassed);

            TestResultStatus status = (statusMatches && allBodyAssertionsPassed)
                    ? TestResultStatus.PASSED
                    : TestResultStatus.FAILED;

            return new TestCaseResult(
                    testCase.getId(), testCase.getName(), testCase.getType(), testCase.getPriority(),
                    status, testCase.getRequest(), httpResponse, assertionResults, null, durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            return new TestCaseResult(
                    testCase.getId(), testCase.getName(), testCase.getType(), testCase.getPriority(),
                    TestResultStatus.ERROR, testCase.getRequest(), null, List.of(), null, durationMs);
        }
    }
}
