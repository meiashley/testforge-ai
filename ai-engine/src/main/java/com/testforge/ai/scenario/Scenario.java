package com.testforge.ai.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scenario {
    private String scenarioId;
    private String name;
    private String description;
    private String expectedOutcome;
    private String businessContext;
}
