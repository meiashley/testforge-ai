package com.testforge.runner.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.testforge.runner.model.AssertionResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import com.testforge.runner.model.TestCaseResult;
import com.testforge.runner.model.TestResultStatus;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ReportWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final String prefix;

    public ReportWriter() {
        this.prefix = "v1";
    }

    public ReportWriter(String prefix) {
        this.prefix = prefix;
    }

    public void write(ExecutionReport report, Path outputDir) {
        writeConsole(report, outputDir);
        writeJson(report, outputDir);
        writeMarkdown(report, outputDir);
    }

    // ── Console ──────────────────────────────────────────────────────────────

    private void writeConsole(ExecutionReport report, Path outputDir) {
        ExecutionSummary s = report.getSummary();
        System.out.println("===== " + prefix.toUpperCase() + " EXECUTION REPORT =====");
        System.out.printf("Total: %d | Passed: %d | Failed: %d | Errors: %d | Pass Rate: %.1f%%%n",
                s.getTotal(), s.getPassed(), s.getFailed(), s.getErrored(), s.getPassRate() * 100);
        System.out.printf("Duration: %dms | Executed: %s%n", s.getTotalDurationMs(), s.getExecutedAt());

        List<TestCaseResult> failures = report.getResults().stream()
                .filter(r -> r.getStatus() == TestResultStatus.FAILED)
                .toList();

        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("FAILURES:");
            for (TestCaseResult r : failures) {
                System.out.printf("  [%s] %-9s | %s | %s%n",
                        r.getTestCaseId(), r.getType(), r.getName(), failOneLiner(r));
            }
        }

        System.out.println();
        System.out.println("Reports written to:");
        System.out.println("  " + outputDir.resolve(prefix + "-execution-report.json"));
        System.out.println("  " + outputDir.resolve(prefix + "-execution-report.md"));
        System.out.println("================================");
    }

    private String failOneLiner(TestCaseResult r) {
        if (r.getAssertionResults().isEmpty()) {
            return "status check failed | actual: " + r.getHttpResponse().getStatusCode();
        }
        return r.getAssertionResults().stream()
                .filter(ar -> !ar.isPassed())
                .map(ar -> "field '" + ar.getField() + "': " + ar.getFailureReason())
                .collect(Collectors.joining("; "));
    }

    // ── JSON ─────────────────────────────────────────────────────────────────

    private void writeJson(ExecutionReport report, Path outputDir) {
        Path file = outputDir.resolve(prefix + "-execution-report.json");
        try {
            Files.createDirectories(outputDir);
            MAPPER.writeValue(file.toFile(), report);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON report to " + file, e);
        }
    }

    // ── Markdown ──────────────────────────────────────────────────────────────

    private void writeMarkdown(ExecutionReport report, Path outputDir) {
        Path file = outputDir.resolve(prefix + "-execution-report.md");
        try {
            Files.createDirectories(outputDir);
            Files.writeString(file, buildMarkdown(report));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Markdown report to " + file, e);
        }
    }

    private String buildMarkdown(ExecutionReport report) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        ExecutionSummary s = report.getSummary();
        out.println("# " + prefix.toUpperCase() + " Execution Report");
        out.println();
        out.printf("Executed: %s | Duration: %dms%n", s.getExecutedAt(), s.getTotalDurationMs());
        out.println();

        // Section 1: Summary table grouped by endpoint
        out.println("## Summary");
        out.println();
        out.println("| Endpoint | Total | Passed | Failed |");
        out.println("|----------|-------|--------|--------|");

        Map<String, List<TestCaseResult>> byEndpoint = groupByEndpoint(report.getResults());
        int grandTotal = 0, grandPassed = 0, grandFailed = 0;
        for (String endpoint : List.of("createPayment", "getPayment", "refundPayment")) {
            List<TestCaseResult> group = byEndpoint.getOrDefault(endpoint, List.of());
            int total = group.size();
            int passed = (int) group.stream().filter(r -> r.getStatus() == TestResultStatus.PASSED).count();
            int failed = (int) group.stream().filter(r -> r.getStatus() == TestResultStatus.FAILED).count();
            grandTotal += total;
            grandPassed += passed;
            grandFailed += failed;
            out.printf("| %s | %d | %d | %d |%n", endpoint, total, passed, failed);
        }
        out.printf("| **Total** | **%d** | **%d** | **%d** |%n", grandTotal, grandPassed, grandFailed);
        out.println();

        // Section 2: Failure Categories
        out.println("## Failure Categories");
        out.println();
        out.println("| Category | Count |");
        out.println("|----------|-------|");

        Map<String, Long> categoryCounts = report.getResults().stream()
                .filter(r -> r.getStatus() == TestResultStatus.FAILED && r.getFailureCategory() != null)
                .collect(Collectors.groupingBy(TestCaseResult::getFailureCategory, Collectors.counting()));

        if (categoryCounts.isEmpty()) {
            out.println("| (none) | 0 |");
        } else {
            categoryCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> out.printf("| %s | %d |%n", e.getKey(), e.getValue()));
        }
        out.println();

        // Section 3: Representative Failures (up to 3)
        out.println("## Representative Failures");
        out.println();

        List<TestCaseResult> failures = report.getResults().stream()
                .filter(r -> r.getStatus() == TestResultStatus.FAILED)
                .limit(3)
                .toList();

        if (failures.isEmpty()) {
            out.println("No failures.");
        } else {
            for (TestCaseResult r : failures) {
                out.printf("### [%s] %s (%s)%n", r.getTestCaseId(), r.getName(), r.getType());
                out.printf("- **Category**: %s%n", r.getFailureCategory() != null ? r.getFailureCategory() : "N/A");
                out.printf("- **Actual status**: %d%n", r.getHttpResponse().getStatusCode());
                if (!r.getAssertionResults().isEmpty()) {
                    out.println("- **Assertion results**:");
                    for (AssertionResult ar : r.getAssertionResults()) {
                        String mark = ar.isPassed() ? "✓" : "✗";
                        out.printf("  - `%s`: expected `%s` → actual `%s` %s%n",
                                ar.getField(), ar.getExpected(), ar.getActual(), mark);
                    }
                }
                out.println();
            }
        }

        // Section 4: V2 Prompt Improvement Targets
        out.println("## V2 Prompt Improvement Targets");
        out.println();
        out.println(buildImprovementTargets(report, categoryCounts));

        return sw.toString();
    }

    private String buildImprovementTargets(ExecutionReport report, Map<String, Long> categoryCounts) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        out.println("Based on V1 baseline failure patterns:");
        out.println();

        if (categoryCounts.getOrDefault("Hardcoded dynamic value", 0L) > 0) {
            out.println("- **Hardcoded dynamic IDs**: Instruct Claude to use `non-null` for all dynamic id fields (e.g. `id`, `paymentId`) instead of hardcoding `pay_*` literals.");
        }
        if (categoryCounts.getOrDefault("Resource not found (likely hardcoded id)", 0L) > 0) {
            out.println("- **Hardcoded path IDs**: Instruct Claude to use a known seed payment ID returned by a prior create call, or use `non-null` for GET assertions.");
        }
        if (categoryCounts.getOrDefault("Validation expectation incorrect", 0L) > 0) {
            out.println("- **Incorrect status expectations**: Review negative test cases where expected status doesn't match the API's actual validation behavior.");
        }
        if (categoryCounts.getOrDefault("Type mismatch", 0L) > 0) {
            out.println("- **Type mismatches**: Instruct Claude to match assertion value types to the API schema (e.g. numbers as integers, not strings).");
        }

        long uncategorized = categoryCounts.getOrDefault("Uncategorized", 0L);
        if (uncategorized > 0) {
            out.printf("- **Uncategorized failures** (%d): Manual review required to identify root causes and expand the categorization heuristic.%n", uncategorized);
        }

        if (categoryCounts.isEmpty()) {
            out.println("- All tests passed — no prompt improvements required for V2.");
        }

        return sw.toString().trim();
    }

    private Map<String, List<TestCaseResult>> groupByEndpoint(List<TestCaseResult> results) {
        Map<String, List<TestCaseResult>> map = new LinkedHashMap<>();
        map.put("createPayment", new ArrayList<>());
        map.put("getPayment", new ArrayList<>());
        map.put("refundPayment", new ArrayList<>());

        for (TestCaseResult r : results) {
            String name = r.getName().toLowerCase();
            if (name.contains("create")) {
                map.get("createPayment").add(r);
            } else if (name.contains("refund")) {
                map.get("refundPayment").add(r);
            } else {
                map.get("getPayment").add(r);
            }
        }
        return map;
    }
}
