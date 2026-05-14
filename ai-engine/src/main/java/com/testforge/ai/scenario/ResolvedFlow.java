package com.testforge.ai.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedFlow {
    private String flowId;
    private String featureId;
    private String description;

    @Builder.Default
    private List<FlowStep> steps = new ArrayList<>();
}
