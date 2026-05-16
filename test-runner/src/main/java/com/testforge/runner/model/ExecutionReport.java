package com.testforge.runner.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.ai.consistency.AlignmentResult;
import com.testforge.ai.requirement.RequirementAnalysis;
import com.testforge.runner.execution.PlanExecutionResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExecutionReport {
    private final ExecutionSummary summary;
    private final List<TestCaseResult> results;
    private List<FailureAnalysisResult> failureAnalysis;

    // V5 fields — null/empty when not used (V3/V4 reports unaffected)
    private AlignmentResult consistencyResult;
    private List<PlanExecutionResult> scenarioResults;
    private RequirementAnalysis requirementAnalysis;

    @JsonCreator
    public ExecutionReport(@JsonProperty("summary") ExecutionSummary summary,
                           @JsonProperty("results") List<TestCaseResult> results) {
        this.summary = summary;
        this.results = results;
        this.failureAnalysis = new ArrayList<>();
    }
}
