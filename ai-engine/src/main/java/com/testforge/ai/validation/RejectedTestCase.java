package com.testforge.ai.validation;

import lombok.Value;

import java.util.List;

@Value
public class RejectedTestCase {
    int batchIndex;
    String testCaseId;
    List<ValidationIssue> validationIssues;

    public RejectedTestCase(int batchIndex, String testCaseId, List<ValidationIssue> validationIssues) {
        this.batchIndex = batchIndex;
        this.testCaseId = testCaseId;
        this.validationIssues = List.copyOf(validationIssues);
    }
}
