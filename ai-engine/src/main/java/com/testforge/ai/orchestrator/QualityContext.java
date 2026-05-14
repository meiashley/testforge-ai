package com.testforge.ai.orchestrator;

import com.testforge.ai.analysis.FailureAnalysisResult;
import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.requirement.RequirementAnalysis;
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
public class QualityContext {

    private String specPath;
    private String requirementPath;
    private String openApiSpecContent;
    private String requirementContent;

    @Builder.Default
    private List<EndpointSpec> endpoints = new ArrayList<>();

    private RequirementAnalysis requirementAnalysis;

    @Builder.Default
    private List<FailureAnalysisResult> failureAnalysis = new ArrayList<>();
}
