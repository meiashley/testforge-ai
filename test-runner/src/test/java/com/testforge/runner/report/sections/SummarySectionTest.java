package com.testforge.runner.report.sections;

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
        assertTrue(html.contains("2026-05-16"));
    }
}
