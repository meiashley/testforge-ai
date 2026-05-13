package com.testforge.ai.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureAnalysisInput {
    private String testCaseId;
    private String testCaseName;
    private String testCaseType;
    private String endpointMethod;
    private String endpointPath;
    private String requestBody;
    private int actualStatusCode;
    private String actualResponseBody;
    private int expectedStatusCode;
    private String assertionFailures;
}
