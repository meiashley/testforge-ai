package com.testforge.runner.model;

import com.testforge.ai.analysis.FailureAnalysisResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutionReport {
    private final ExecutionSummary summary;
    private final List<TestCaseResult> results;
    private List<FailureAnalysisResult> failureAnalysis;

    public ExecutionReport(ExecutionSummary summary, List<TestCaseResult> results) {
        this.summary = summary;
        this.results = results;
        this.failureAnalysis = new ArrayList<>();
    }
}
