package com.testforge.runner.report.sections;

import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FailureAnalysisSectionTest {

    private final FailureAnalysisSection section = new FailureAnalysisSection();

    private ExecutionReport emptyReport() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        return new ExecutionReport(s, List.of());
    }

    private ExecutionReport reportWithAnalysis() {
        FailureAnalysisResult r = FailureAnalysisResult.builder()
                .testCaseId("tc-1").testCaseName("Create payment fails")
                .rootCauseCategory("API_BUG")
                .rootCauseSummary("API returns wrong status code")
                .evidence("Expected 201 but got 400")
                .suggestedFix("Fix the API endpoint validation")
                .confidence("HIGH")
                .build();
        ExecutionSummary s = new ExecutionSummary(1, 0, 1, 0, 0.0, "2026-05-16T00:00:00Z", 50);
        ExecutionReport report = new ExecutionReport(s, List.of());
        report.setFailureAnalysis(List.of(r));
        return report;
    }

    @Test
    void hasContent_falseWhenNoAnalysis() {
        assertFalse(section.hasContent(emptyReport()));
    }

    @Test
    void hasContent_trueWhenAnalysisPresent() {
        assertTrue(section.hasContent(reportWithAnalysis()));
    }

    @Test
    void render_containsTestCaseNameAndCategory() {
        String html = section.render(reportWithAnalysis());
        assertTrue(html.contains("Create payment fails"));
        assertTrue(html.contains("API_BUG") || html.contains("API BUG"));
        assertTrue(html.contains("API returns wrong status code"));
        assertTrue(html.contains("Fix the API endpoint validation"));
    }
}
