package com.testforge.ai.requirement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequirementConstraint {
    private String constraintId;
    private String category;
    private String subject;
    private String description;
    private String expectedBehavior;
    private String requirementSection;
    private Map<String, Object> parameters;
}
