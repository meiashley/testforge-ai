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
public class ScenarioStep {
    private int order;
    private String stepId;
    private String role;
    private String method;
    private String pathTemplate;
    private Map<String, String> pathBindings;
    private Map<String, String> headerBindings;
    private Object bodyBinding;
    private String requestBody;
    private Map<String, String> outputCapture;
    private int expectedStatusCode;

    @Builder.Default
    private List<Assertion> assertions = new ArrayList<>();

    private String stepDescription;
}
