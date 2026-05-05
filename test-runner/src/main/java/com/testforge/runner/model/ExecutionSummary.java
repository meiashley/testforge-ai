package com.testforge.runner.model;

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
}
