package com.testforge.runner.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class ExecutionSummary {
    int total;
    int passed;
    int failed;
    int errored;
    double passRate;
    String executedAt;
    long totalDurationMs;

    @JsonCreator
    public ExecutionSummary(@JsonProperty("total") int total,
                            @JsonProperty("passed") int passed,
                            @JsonProperty("failed") int failed,
                            @JsonProperty("errored") int errored,
                            @JsonProperty("passRate") double passRate,
                            @JsonProperty("executedAt") String executedAt,
                            @JsonProperty("totalDurationMs") long totalDurationMs) {
        this.total = total;
        this.passed = passed;
        this.failed = failed;
        this.errored = errored;
        this.passRate = passRate;
        this.executedAt = executedAt;
        this.totalDurationMs = totalDurationMs;
    }
}
