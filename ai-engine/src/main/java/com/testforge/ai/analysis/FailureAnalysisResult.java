package com.testforge.ai.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureAnalysisResult {
    private String testCaseId;
    private String testCaseName;
    private String rootCauseCategory;
    private String rootCauseSummary;
    private String evidence;
    private String suggestedFix;
    private String confidence;
}
