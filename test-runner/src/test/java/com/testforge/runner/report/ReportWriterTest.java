package com.testforge.runner.report;

import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {

    @TempDir
    Path outputDir;

    @Test
    void markdownUsesReadableUtcTimeAndJsonKeepsIsoTimestamp() throws Exception {
        String timestamp = "2026-07-08T02:12:41.841590Z";
        ExecutionReport report = new ExecutionReport(
                new ExecutionSummary(0, 0, 0, 0, 0.0, timestamp, 596),
                List.of());

        new ReportWriter("v5").write(report, outputDir);

        String markdown = Files.readString(outputDir.resolve("v5-execution-report.md"));
        String json = Files.readString(outputDir.resolve("v5-execution-report.json"));

        assertTrue(markdown.contains("Executed: 8 Jul 2026, 02:12 UTC | Duration: 596ms"));
        assertTrue(json.contains("\"executedAt\" : \"" + timestamp + "\""));
    }
}
