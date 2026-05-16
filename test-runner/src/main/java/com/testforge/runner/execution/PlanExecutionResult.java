package com.testforge.runner.execution;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanExecutionResult {
    private String planId;
    private String scenarioId;
    private String scenarioName;
    private List<StepResult> steps;
    private boolean passed;
}
