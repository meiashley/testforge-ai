package com.testforge.runner.report.sections;

import com.testforge.runner.model.CoverageMetric;
import com.testforge.runner.model.CoverageStatus;
import com.testforge.runner.model.CoverageSummary;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverageSectionTest {

    private final CoverageSection section = new CoverageSection();

    @Test
    void pendingShowsMessageAndNoLink() {
        ExecutionReport report = report(CoverageSummary.pending("mock-banking-api"));

        String html = section.render(report);

        assertTrue(html.contains("mock-banking-api"));
        assertTrue(html.contains("Coverage data is being generated"));
        assertFalse(html.contains("coverage/jacoco/index.html"));
    }

    @Test
    void availableShowsMetricsAndRelativeLink() {
        ExecutionReport report = report(availableCoverage("coverage/jacoco/index.html"));

        String html = section.render(report);

        assertTrue(html.contains("Lines"));
        assertTrue(html.contains("Branches"));
        assertTrue(html.contains("90.0%"));
        assertTrue(html.contains("coverage/jacoco/index.html"));
        assertTrue(html.contains("View JaCoCo Details &rarr;"));
        assertTrue(html.contains("target=\"_blank\""));
        assertTrue(html.contains("class=\"coverage-link action-link\""));
    }

    @Test
    void availableWithoutDetailsPathDoesNotShowLink() {
        ExecutionReport report = report(availableCoverage(null));

        String html = section.render(report);

        assertFalse(html.contains("View detailed JaCoCo report"));
    }

    @Test
    void errorShowsMessageAndNoZeroCoverage() {
        ExecutionReport report = report(CoverageSummary.error("mock-banking-api", "parse failed"));

        String html = section.render(report);

        assertTrue(html.contains("parse failed"));
        assertFalse(html.contains("0.0%"));
    }

    @Test
    void availableUsesStableTableColumnClasses() {
        ExecutionReport report = report(availableCoverage("coverage/jacoco/index.html"));

        String html = section.render(report);

        assertTrue(html.contains("class=\"coverage-table\""));
        assertTrue(html.contains("<th class=\"metric-col\">Metric</th>"));
        assertTrue(html.contains("<th class=\"number-col\">Covered</th>"));
        assertTrue(html.contains("<td class=\"metric-col\">Lines</td>"));
        assertTrue(html.contains("<td class=\"number-col\">9</td>"));
    }

    private ExecutionReport report(CoverageSummary coverage) {
        ExecutionReport report = new ExecutionReport(
                new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-07-07T00:00:00Z", 0),
                List.of());
        report.setCoverage(coverage);
        return report;
    }

    private CoverageSummary availableCoverage(String detailsPath) {
        CoverageSummary coverage = CoverageSummary.pending("mock-banking-api");
        coverage.setStatus(CoverageStatus.AVAILABLE);
        coverage.setLine(CoverageMetric.of(9, 1));
        coverage.setBranch(CoverageMetric.of(8, 2));
        coverage.setInstruction(CoverageMetric.of(90, 10));
        coverage.setMethod(CoverageMetric.of(18, 2));
        coverage.setClazz(CoverageMetric.of(8, 0));
        coverage.setComplexity(CoverageMetric.of(22, 3));
        coverage.setDetailsPath(detailsPath);
        return coverage;
    }
}
