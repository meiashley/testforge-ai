package com.testforge.ai.mapping;

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
public class EndpointMapping {
    private String featureId;
    private String featureName;
    private String featureDescription;

    @Builder.Default
    private List<EndpointReference> endpoints = new ArrayList<>();

    private String requirementSection;
}
