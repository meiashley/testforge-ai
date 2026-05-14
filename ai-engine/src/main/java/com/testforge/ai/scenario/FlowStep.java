package com.testforge.ai.scenario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStep {
    private int order;
    private String stepId;
    private String role;
    private String method;
    private String pathTemplate;
    private Map<String, String> pathBindings;
    private Map<String, String> headerBindings;
    private String bodyBinding;
    private Map<String, String> outputCapture;
}
