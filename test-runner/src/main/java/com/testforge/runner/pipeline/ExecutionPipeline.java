package com.testforge.runner.pipeline;

import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.runner.assertion.AssertionEvaluator;
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

public class ExecutionPipeline {

    private final HttpExecutor httpExecutor;
    private final AssertionEvaluator assertionEvaluator;
    private final ReportBuilder reportBuilder;
    private final ReportWriter reportWriter;

    public ExecutionPipeline(HttpExecutor httpExecutor, AssertionEvaluator assertionEvaluator,
                             ReportBuilder reportBuilder, ReportWriter reportWriter) {
        this.httpExecutor = httpExecutor;
        this.assertionEvaluator = assertionEvaluator;
        this.reportBuilder = reportBuilder;
        this.reportWriter = reportWriter;
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
        Path outputDir = Path.of(System.getProperty("project.basedir", "."), "target");
        reportWriter.write(report, outputDir);
        return report;
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
                    status, httpResponse, assertionResults, null, durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            return new TestCaseResult(
                    testCase.getId(), testCase.getName(), testCase.getType(), testCase.getPriority(),
                    TestResultStatus.ERROR, null, List.of(), null, durationMs);
        }
    }
}
