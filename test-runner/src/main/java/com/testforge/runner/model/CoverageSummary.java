package com.testforge.runner.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoverageSummary {
    private CoverageStatus status;
    private String targetModule;
    private CoverageMetric line;
    private CoverageMetric branch;
    private CoverageMetric instruction;
    private CoverageMetric method;
    @JsonProperty("class")
    private CoverageMetric clazz;
    private CoverageMetric complexity;
    private String detailsPath;
    private String generatedAt;
    private String message;
    private String execDataPath;

    public static CoverageSummary pending(String targetModule) {
        return new CoverageSummary(
                CoverageStatus.PENDING,
                targetModule,
                null, null, null, null, null, null,
                null,
                null,
                "Coverage data is being generated. Refresh this report after the run completes.",
                null);
    }

    public static CoverageSummary unavailable(String targetModule, String message) {
        return new CoverageSummary(
                CoverageStatus.UNAVAILABLE,
                targetModule,
                null, null, null, null, null, null,
                null,
                Instant.now().toString(),
                message,
                null);
    }

    public static CoverageSummary error(String targetModule, String message) {
        return new CoverageSummary(
                CoverageStatus.ERROR,
                targetModule,
                null, null, null, null, null, null,
                null,
                Instant.now().toString(),
                message,
                null);
    }
}
