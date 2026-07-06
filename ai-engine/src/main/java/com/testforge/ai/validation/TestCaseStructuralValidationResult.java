package com.testforge.ai.validation;

import com.testforge.ai.model.TestCase;
import lombok.Value;

import java.util.List;

@Value
public class TestCaseStructuralValidationResult {
    List<TestCase> acceptedTestCases;
    List<RejectedTestCase> rejectedTestCases;
    List<ValidationIssue> issues;
    boolean batchRejected;

    public TestCaseStructuralValidationResult(List<TestCase> acceptedTestCases,
                                              List<ValidationIssue> issues,
                                              boolean batchRejected) {
        this(acceptedTestCases, issues, batchRejected, List.of());
    }

    public TestCaseStructuralValidationResult(List<TestCase> acceptedTestCases,
                                              List<ValidationIssue> issues,
                                              boolean batchRejected,
                                              List<RejectedTestCase> rejectedTestCases) {
        this.acceptedTestCases = List.copyOf(acceptedTestCases);
        this.issues = List.copyOf(issues);
        this.batchRejected = batchRejected;
        this.rejectedTestCases = List.copyOf(rejectedTestCases);
    }

    public List<ValidationIssue> errors() {
        return issues.stream()
                .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR)
                .toList();
    }

    public List<ValidationIssue> warnings() {
        return issues.stream()
                .filter(issue -> issue.getSeverity() == ValidationSeverity.WARNING)
                .toList();
    }

    public boolean hasErrors() {
        return issues.stream()
                .anyMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR);
    }
}
