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

    private ExecutionReport reportWithMultipleCategories() {
        FailureAnalysisResult apiBug = FailureAnalysisResult.builder()
                .testCaseId("tc-1").testCaseName("Refund status wrong")
                .rootCauseCategory("API_BUG")
                .rootCauseSummary("API returns wrong status code")
                .confidence("HIGH")
                .build();
        FailureAnalysisResult logicError = FailureAnalysisResult.builder()
                .testCaseId("tc-2").testCaseName("Verifier expects missing field")
                .rootCauseCategory("TEST_LOGIC_ERROR")
                .rootCauseSummary("Assertion does not match spec")
                .confidence("HIGH")
                .build();
        FailureAnalysisResult secondApiBug = FailureAnalysisResult.builder()
                .testCaseId("tc-3").testCaseName("Duplicate refund accepted")
                .rootCauseCategory("API_BUG")
                .rootCauseSummary("Refund endpoint allows invalid transition")
                .confidence("MEDIUM")
                .build();
        ExecutionSummary s = new ExecutionSummary(3, 0, 3, 0, 0.0, "2026-05-16T00:00:00Z", 50);
        ExecutionReport report = new ExecutionReport(s, List.of());
        report.setFailureAnalysis(List.of(logicError, secondApiBug, apiBug));
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

    @Test
    void render_groupsAnalysesByRootCauseCategory() {
        String html = section.render(reportWithMultipleCategories());

        assertTrue(html.contains("<h3>API BUG</h3>"));
        assertTrue(html.contains("<h3>TEST LOGIC ERROR</h3>"));

        int apiHeader = html.indexOf("<h3>API BUG</h3>");
        int firstApiCase = html.indexOf("Refund status wrong");
        int secondApiCase = html.indexOf("Duplicate refund accepted");
        int logicHeader = html.indexOf("<h3>TEST LOGIC ERROR</h3>");
        int logicCase = html.indexOf("Verifier expects missing field");

        assertTrue(apiHeader >= 0);
        assertTrue(firstApiCase > apiHeader);
        assertTrue(secondApiCase > apiHeader);
        assertTrue(logicHeader > secondApiCase);
        assertTrue(logicCase > logicHeader);
    }
}
