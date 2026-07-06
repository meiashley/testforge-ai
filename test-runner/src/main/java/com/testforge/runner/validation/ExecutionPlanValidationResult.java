package com.testforge.runner.validation;

import com.testforge.ai.validation.ValidationIssue;
import com.testforge.ai.validation.ValidationSeverity;
import lombok.Value;

import java.util.List;

@Value
public class ExecutionPlanValidationResult {
    List<ValidationIssue> issues;

    public ExecutionPlanValidationResult(List<ValidationIssue> issues) {
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> errors() {
        return issues.stream()
                .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR)
                .toList();
    }

    public boolean isValid() {
        return errors().isEmpty();
    }
}
