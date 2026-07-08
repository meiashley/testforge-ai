package com.testforge.runner.report.sections;

import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.model.CoverageMetric;
import com.testforge.runner.model.CoverageStatus;
import com.testforge.runner.model.CoverageSummary;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SummarySectionTest {

    private final SummarySection section = new SummarySection();

    private ExecutionReport emptyReport() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        return new ExecutionReport(s, List.of());
    }

    @Test
    void hasContent_alwaysTrue() {
        assertTrue(section.hasContent(emptyReport()));
    }

    @Test
    void render_containsApiTotalAndPassRate() {
        ExecutionSummary s = new ExecutionSummary(10, 8, 2, 0, 0.8, "2026-05-16T00:00:00Z", 500);
        ExecutionReport report = new ExecutionReport(s, List.of());
        String html = section.render(report);
        assertTrue(html.contains("10"));
        assertTrue(html.contains("80.0%"));
        assertTrue(html.contains("16 May 2026, 00:00 UTC"));
    }

    @Test
    void render_labelsScenarioCardAsPassedCount() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        ExecutionReport report = new ExecutionReport(s, List.of());
        report.setScenarioResults(List.of(
                scenario(true),
                scenario(true),
                scenario(true),
                scenario(false),
                scenario(false),
                scenario(false)));

        String html = section.render(report);

        assertTrue(html.contains("Scenarios Passed"));
        assertTrue(html.contains(">3/6<"));
        assertFalse(html.contains("<div class=\"label\">Scenarios</div>"));
    }

    @Test
    void render_formatsGeneratedTimeAsReadableUtc() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-07-08T02:12:41.841590Z", 596);
        ExecutionReport report = new ExecutionReport(s, List.of());

        String html = section.render(report);

        assertTrue(html.contains("Generated 8 Jul 2026, 02:12 UTC"));
        assertFalse(html.contains("2026-07-08T02:12:41.841590Z"));
    }

    @Test
    void render_handlesNullGeneratedTime() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, null, 596);
        ExecutionReport report = new ExecutionReport(s, List.of());

        String html = section.render(report);

        assertTrue(html.contains("Generated -"));
    }

    @Test
    void render_pendingCoverageShowsDashesAndTargetModule() {
        ExecutionReport report = emptyReport();
        report.setCoverage(CoverageSummary.pending("mock-banking-api"));

        String html = section.render(report);

        assertTrue(html.contains("Target API"));
        assertTrue(html.contains("mock-banking-api"));
        assertTrue(html.contains("Line Coverage"));
        assertTrue(html.contains("Branch Coverage"));
        assertTrue(html.contains("<div class=\"value\">-</div>"));
    }

    @Test
    void render_availableCoverageShowsLineAndBranchPercentages() {
        ExecutionReport report = emptyReport();
        CoverageSummary coverage = CoverageSummary.pending("mock-banking-api");
        coverage.setStatus(CoverageStatus.AVAILABLE);
        coverage.setLine(CoverageMetric.of(9, 1));
        coverage.setBranch(CoverageMetric.of(8, 2));
        report.setCoverage(coverage);

        String html = section.render(report);

        assertTrue(html.contains("90.0%"));
        assertTrue(html.contains("80.0%"));
    }

    @Test
    void render_errorCoverageDoesNotShowZeroPercent() {
        ExecutionReport report = emptyReport();
        report.setCoverage(CoverageSummary.error("mock-banking-api", "parse failed"));

        String html = section.render(report);

        assertTrue(html.contains("Line Coverage"));
        assertTrue(html.contains("Branch Coverage"));
        assertTrue(html.contains("<div class=\"value\">-</div>"));
    }

    private PlanExecutionResult scenario(boolean passed) {
        return PlanExecutionResult.builder()
                .planId("plan")
                .scenarioId("scenario")
                .scenarioName("Scenario")
                .steps(List.of())
                .passed(passed)
                .build();
    }
}
