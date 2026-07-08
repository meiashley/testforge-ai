package com.testforge.runner.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverageSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesPendingCoverage() throws Exception {
        ExecutionReport report = new ExecutionReport(
                new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-07-07T00:00:00Z", 0),
                List.of());
        report.setCoverage(CoverageSummary.pending("mock-banking-api"));

        JsonNode json = mapper.valueToTree(report);

        assertEquals("PENDING", json.get("coverage").get("status").asText());
        assertEquals("mock-banking-api", json.get("coverage").get("targetModule").asText());
    }

    @Test
    void serializesAvailableCoverageWithNumericPercentage() throws Exception {
        CoverageSummary coverage = CoverageSummary.pending("mock-banking-api");
        coverage.setStatus(CoverageStatus.AVAILABLE);
        coverage.setLine(CoverageMetric.of(9, 1));
        coverage.setClazz(CoverageMetric.of(1, 0));
        ExecutionReport report = new ExecutionReport(
                new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-07-07T00:00:00Z", 0),
                List.of());
        report.setCoverage(coverage);

        JsonNode json = mapper.valueToTree(report);

        assertTrue(json.get("coverage").get("line").get("percentage").isNumber());
        assertEquals(90.0, json.get("coverage").get("line").get("percentage").asDouble(), 0.0001);
        assertNotNull(json.get("coverage").get("class"));
        assertNull(json.get("coverage").get("clazz"));
    }

    @Test
    void oldJsonWithoutCoverageStillDeserializes() throws Exception {
        ExecutionReport report = mapper.readValue("""
                {
                  "summary": {
                    "total": 0,
                    "passed": 0,
                    "failed": 0,
                    "errored": 0,
                    "passRate": 0.0,
                    "executedAt": "2026-07-07T00:00:00Z",
                    "totalDurationMs": 0
                  },
                  "results": []
                }
                """, ExecutionReport.class);

        assertNull(report.getCoverage());
        assertEquals(0, report.getSummary().getTotal());
    }
}
