package com.testforge.runner.report.sections;

import com.testforge.ai.consistency.AlignmentResult;
import com.testforge.ai.consistency.ConsistencyMismatch;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsistencySectionTest {

    private final ConsistencySection section = new ConsistencySection();

    private ExecutionReport reportWithNoConsistency() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        return new ExecutionReport(s, List.of());
    }

    private ExecutionReport reportWithMismatch() {
        ConsistencyMismatch m = ConsistencyMismatch.builder()
                .mismatchId("mm-001")
                .category("MISSING_IN_API")
                .severity("HIGH")
                .confidence("HIGH")
                .summary("Cross-user refund check missing")
                .evidence("Section 4.3 not in spec")
                .location("POST /api/payments/{id}/refund")
                .requirementReference("Section 4.3")
                .specReference("/api/payments/{id}/refund")
                .build();
        AlignmentResult result = AlignmentResult.builder()
                .mismatches(List.of(m))
                .totalConstraints(5)
                .alignedCount(4)
                .mismatchCount(1)
                .severityBreakdown(Map.of("HIGH", 1))
                .categoryBreakdown(Map.of("MISSING_IN_API", 1))
                .build();
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        ExecutionReport report = new ExecutionReport(s, List.of());
        report.setConsistencyResult(result);
        return report;
    }

    @Test
    void hasContent_falseWhenNoConsistencyResult() {
        assertFalse(section.hasContent(reportWithNoConsistency()));
    }

    @Test
    void hasContent_trueWhenMismatchesPresent() {
        assertTrue(section.hasContent(reportWithMismatch()));
    }

    @Test
    void render_containsMismatchSummaryAndEvidence() {
        String html = section.render(reportWithMismatch());
        assertTrue(html.contains("Cross-user refund check missing"));
        assertTrue(html.contains("Section 4.3 not in spec"));
        assertTrue(html.contains("MISSING_IN_API"));
        assertTrue(html.contains("HIGH"));
    }
}
