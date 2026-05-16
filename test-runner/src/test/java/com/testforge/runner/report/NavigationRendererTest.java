package com.testforge.runner.report;

import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import com.testforge.runner.report.sections.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NavigationRendererTest {

    private final NavigationRenderer renderer = new NavigationRenderer();

    private ExecutionReport emptyReport() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        return new ExecutionReport(s, List.of());
    }

    @Test
    void render_containsAnchorLinksForContentSections() {
        List<ReportSection> sections = List.of(
                new SummarySection(),       // hasContent always true
                new ConsistencySection(),   // hasContent false (no data)
                new FailureAnalysisSection() // hasContent false (no data)
        );

        String html = renderer.render(sections, emptyReport());

        assertTrue(html.contains("href=\"#summary\""), "should link to summary");
        assertFalse(html.contains("href=\"#consistency\""), "should not link to consistency when no data");
        assertFalse(html.contains("href=\"#failure-analysis\""), "should not link to ai-analysis when no data");
        assertTrue(html.contains("<nav"), "should have nav element");
        assertTrue(html.contains("📊"), "should contain summary icon");
    }
}
