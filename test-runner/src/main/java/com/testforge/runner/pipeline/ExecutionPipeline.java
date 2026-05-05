package com.testforge.runner.pipeline;

import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.*;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ExecutionPipeline {

    private final HttpExecutor httpExecutor = new HttpExecutor();
    private final AssertionEvaluator assertionEvaluator = new AssertionEvaluator();
    private final ReportBuilder reportBuilder = new ReportBuilder();
    private final ReportWriter reportWriter = new ReportWriter();

    public ExecutionReport run(List<GenerationResult> generationResults, String baseUrl) {
        List<TestCaseResult> results = new ArrayList<>();

        for (GenerationResult generation : generationResults) {
            for (TestCase testCase : generation.getTestCases()) {
                results.add(execute(testCase, baseUrl));
            }
        }

        ExecutionReport report = reportBuilder.build(results);
        reportWriter.write(report, Path.of("target"));
        return report;
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
