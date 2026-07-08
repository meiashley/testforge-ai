package com.testforge.runner.report;

import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.ai.consistency.AlignmentResult;
import com.testforge.ai.consistency.ConsistencyMismatch;
import com.testforge.ai.model.TestCaseType;
import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.model.AssertionResult;
import com.testforge.runner.model.CoverageMetric;
import com.testforge.runner.model.CoverageStatus;
import com.testforge.runner.model.CoverageSummary;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import com.testforge.runner.model.HttpResponse;
import com.testforge.runner.model.TestCaseResult;
import com.testforge.runner.model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HtmlReportGeneratorTest {

    private HtmlReportGenerator generator;
    private ExecutionReport report;

    @BeforeEach
    void setUp() {
        generator = new HtmlReportGenerator();

        ExecutionSummary summary = new ExecutionSummary(2, 1, 1, 0, 50.0, "2026-05-08T04:00:00Z", 120);

        HttpResponse response = new HttpResponse(200, Map.of("id", "1"), "{\"id\":\"1\"}", Map.of(), 60);

        AssertionResult passed = new AssertionResult("status", 200, 200, true, null);
        AssertionResult failed = new AssertionResult("body.id", "abc", "xyz", false, "mismatch");

        TestCaseResult tc1 = new TestCaseResult(
                "tc-1", "createPayment_happy", TestCaseType.HAPPY_PATH, null,
                TestResultStatus.PASSED, null, response, List.of(passed), null, 60);

        TestCaseResult tc2 = new TestCaseResult(
                "tc-2", "createPayment_negative", TestCaseType.NEGATIVE, null,
                TestResultStatus.FAILED, null, response, List.of(passed, failed), "ASSERTION_FAILURE", 60);

        report = new ExecutionReport(summary, List.of(tc1, tc2));
    }

    @Test
    void outputContainsTitle() {
        String html = generator.generate(report);
        assertTrue(html.contains("TestForge AI Execution Report"));
    }

    @Test
    void outputContainsPassAndTotalNumbers() {
        String html = generator.generate(report);
        assertTrue(html.contains(">1<"), "should contain passed count 1");
        assertTrue(html.contains(">2<"), "should contain total count 2");
    }

    @Test
    void outputContainsEndpointName() {
        String html = generator.generate(report);
        assertTrue(html.contains("createPayment"));
    }

    @Test
    void outputIsSelfContainedNoExternalUrls() {
        String html = generator.generate(report);
        assertFalse(html.contains("https://"), "should not reference external https URLs");
        assertFalse(html.contains("http://"),  "should not reference external http URLs");
    }

    @Test
    void coverageStylesUseFixedLayoutAndTabularNumbers() {
        String html = generator.generate(reportWithCoverage());

        assertTrue(html.contains("table.coverage-table { width: 100%; table-layout: fixed; border-collapse: collapse;"));
        assertTrue(html.contains("table.coverage-table .metric-col { width: 28%; text-align: left; }"));
        assertTrue(html.contains("table.coverage-table .number-col { width: 18%; text-align: right;"));
        assertTrue(html.contains("font-variant-numeric: tabular-nums;"));
        assertTrue(html.contains(".coverage-link.action-link:focus-visible"));
        assertFalse(html.contains("outline: none"));
    }

    @Test
    void sectionsRenderInExpectedOrder() {
        ExecutionReport fullReport = reportWithAllSections();

        String html = generator.generate(fullReport);

        assertInOrder(html,
                "<section id=\"summary\">",
                "<section id=\"consistency\">",
                "<section id=\"scenarios\">",
                "<section id=\"api-tests\">",
                "<section id=\"coverage\">",
                "<section id=\"failure-analysis\">");
        assertInOrder(html,
                "href=\"#summary\"",
                "href=\"#consistency\"",
                "href=\"#scenarios\"",
                "href=\"#api-tests\"",
                "href=\"#coverage\"",
                "href=\"#failure-analysis\"");
    }

    private ExecutionReport reportWithCoverage() {
        ExecutionReport withCoverage = new ExecutionReport(report.getSummary(), report.getResults());
        withCoverage.setCoverage(availableCoverage());
        return withCoverage;
    }

    private ExecutionReport reportWithAllSections() {
        ExecutionReport fullReport = reportWithCoverage();
        fullReport.setConsistencyResult(AlignmentResult.builder()
                .totalConstraints(1)
                .alignedCount(0)
                .constraintsWithMismatchCount(1)
                .mismatchCount(1)
                .severityBreakdown(Map.of("HIGH", 1))
                .mismatches(List.of(ConsistencyMismatch.builder()
                        .mismatchId("m-1")
                        .severity("HIGH")
                        .category("MISSING_IN_API")
                        .summary("Requirement not documented")
                        .confidence("HIGH")
                        .build()))
                .build());
        fullReport.setScenarioResults(List.of(PlanExecutionResult.builder()
                .planId("plan-1")
                .scenarioId("scenario-1")
                .scenarioName("Scenario")
                .steps(List.of())
                .passed(true)
                .build()));
        fullReport.setFailureAnalysis(List.of(FailureAnalysisResult.builder()
                .testCaseId("tc-2")
                .testCaseName("createPayment_negative")
                .rootCauseCategory("API_BUG")
                .rootCauseSummary("Bug")
                .confidence("HIGH")
                .build()));
        return fullReport;
    }

    private CoverageSummary availableCoverage() {
        CoverageSummary coverage = CoverageSummary.pending("mock-banking-api");
        coverage.setStatus(CoverageStatus.AVAILABLE);
        coverage.setLine(CoverageMetric.of(9, 1));
        coverage.setBranch(CoverageMetric.of(8, 2));
        coverage.setInstruction(CoverageMetric.of(90, 10));
        coverage.setMethod(CoverageMetric.of(18, 2));
        coverage.setClazz(CoverageMetric.of(8, 0));
        coverage.setComplexity(CoverageMetric.of(22, 3));
        coverage.setDetailsPath("coverage/jacoco/index.html");
        return coverage;
    }

    private void assertInOrder(String html, String... markers) {
        int previous = -1;
        for (String marker : markers) {
            int current = html.indexOf(marker);
            assertTrue(current > previous, "Expected marker in order: " + marker);
            previous = current;
        }
    }
}
