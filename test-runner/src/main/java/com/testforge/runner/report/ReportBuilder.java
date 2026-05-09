package com.testforge.runner.report;

import com.testforge.runner.model.*;

import java.time.Instant;
import java.util.List;

public class ReportBuilder {

    public ExecutionReport build(List<TestCaseResult> results) {
        List<TestCaseResult> categorized = results.stream()
                .map(this::withCategory)
                .toList();

        int total = categorized.size();
        int passed = (int) categorized.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
        int failed = (int) categorized.stream().filter(r -> r.getStatus() == TestResultStatus.FAILED).count();
        int errored = (int) categorized.stream().filter(r -> r.getStatus() == TestResultStatus.ERROR).count();
        double passRate = total == 0 ? 0.0 : (double) passed / total;
        long totalDurationMs = categorized.stream().mapToLong(TestCaseResult::getDurationMs).sum();

        ExecutionSummary summary = new ExecutionSummary(
                total, passed, failed, errored, passRate,
                Instant.now().toString(), totalDurationMs);

        return new ExecutionReport(summary, categorized);
    }

    private TestCaseResult withCategory(TestCaseResult result) {
        if (result.getStatus() != TestResultStatus.FAILED) {
            return result;
        }
        String category = categorize(result);
        return new TestCaseResult(
                result.getTestCaseId(), result.getName(), result.getType(), result.getPriority(),
                result.getStatus(), result.getRequest(), result.getHttpResponse(), result.getAssertionResults(),
                category, result.getDurationMs());
    }

    private String categorize(TestCaseResult result) {
        // Rule 1: any expected body value starts with "pay_"
        boolean hasPayPrefix = result.getAssertionResults().stream()
                .anyMatch(ar -> ar.getExpected() instanceof String s && s.startsWith("pay_"));
        if (hasPayPrefix) {
            return "Hardcoded dynamic value";
        }

        int statusCode = result.getHttpResponse().getStatusCode();

        // Rule 2: actual 404 (expected 200/201 but resource not found)
        if (statusCode == 404) {
            return "Resource not found (likely hardcoded id)";
        }

        // Rule 3: actual 2xx (expected 4xx for a validation test)
        if (statusCode >= 200 && statusCode < 300) {
            return "Validation expectation incorrect";
        }

        // Rule 4: any assertion failure reason contains "type mismatch"
        boolean hasTypeMismatch = result.getAssertionResults().stream()
                .anyMatch(ar -> ar.getFailureReason() != null && ar.getFailureReason().contains("type mismatch"));
        if (hasTypeMismatch) {
            return "Type mismatch";
        }

        return "Uncategorized";
    }
}
