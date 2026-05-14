package com.testforge.ai.requirement;

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
public class RequirementAnalysis {

    @Builder.Default
    private List<RequirementConstraint> constraints = new ArrayList<>();

    @Builder.Default
    private List<String> identifiedScenarios = new ArrayList<>();

    private Map<String, Object> metadata;
}
