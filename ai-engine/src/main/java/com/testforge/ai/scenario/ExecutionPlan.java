package com.testforge.ai.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {
    private String planId;
    private String source;
    private String scenarioId;
    private String scenarioName;

    @Builder.Default
    private List<ScenarioStep> steps = new ArrayList<>();

    private Map<String, Object> metadata;
}
