package com.testforge.runner.model;

import lombok.Value;

import java.util.List;

@Value
public class ExecutionReport {
    ExecutionSummary summary;
    List<TestCaseResult> results;
}
