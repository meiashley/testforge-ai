package com.testforge.runner.execution;

import com.testforge.ai.scenario.Assertion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssertionResult {
    private Assertion assertion;
    private boolean passed;
    private String actualValue;
    private String message;
}
