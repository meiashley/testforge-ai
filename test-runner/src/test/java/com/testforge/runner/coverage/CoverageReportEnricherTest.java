package com.testforge.runner.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.testforge.runner.model.CoverageMetric;
import com.testforge.runner.model.CoverageStatus;
import com.testforge.runner.model.CoverageSummary;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverageReportEnricherTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void enrichRewritesJsonHtmlAndMarkdownInSameLocation() throws Exception {
        ExecutionReport report = new ExecutionReport(
                new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-07-07T00:00:00Z", 0),
                List.of());
        report.setCoverage(CoverageSummary.pending("mock-banking-api"));
        Path json = tempDir.resolve("v5-execution-report.json");
        mapper.writeValue(json.toFile(), report);

        CoverageSummary coverage = CoverageSummary.pending("mock-banking-api");
        coverage.setStatus(CoverageStatus.AVAILABLE);
        coverage.setLine(CoverageMetric.of(9, 1));
        coverage.setBranch(CoverageMetric.of(8, 2));
        coverage.setInstruction(CoverageMetric.of(90, 10));
        coverage.setMethod(CoverageMetric.of(18, 2));
        coverage.setClazz(CoverageMetric.of(8, 0));
        coverage.setComplexity(CoverageMetric.of(22, 3));
        coverage.setDetailsPath("coverage/jacoco/index.html");

        new CoverageReportEnricher().enrich(json, tempDir, "v5", coverage);

        JsonNode rewritten = mapper.readTree(json.toFile());
        assertEquals("AVAILABLE", rewritten.get("coverage").get("status").asText());
        String html = Files.readString(tempDir.resolve("v5-execution-report.html"));
        String markdown = Files.readString(tempDir.resolve("v5-execution-report.md"));
        assertTrue(html.contains("Line Coverage"));
        assertTrue(html.contains("Branch Coverage"));
        assertTrue(html.contains("coverage/jacoco/index.html"));
        assertTrue(markdown.contains("## Target API Coverage"));
        assertTrue(markdown.contains("coverage/jacoco/index.html"));
    }
}
