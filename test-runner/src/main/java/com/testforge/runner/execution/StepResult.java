package com.testforge.runner.execution;

import com.testforge.ai.scenario.ScenarioStep;
import com.testforge.runner.model.HttpResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {
    private ScenarioStep step;
    private HttpResponse response;
    private boolean statusMatch;
    private List<AssertionResult> assertionResults;
    private boolean passed;
    private String skipReason;

    public static StepResult skipped(ScenarioStep step) {
        return StepResult.builder()
                .step(step)
                .passed(false)
                .skipReason("Previous step failed")
                .build();
    }
}
