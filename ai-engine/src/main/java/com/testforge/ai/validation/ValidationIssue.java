package com.testforge.ai.validation;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ValidationIssue {
    ValidationSeverity severity;
    String code;
    String fieldPath;
    String message;

    public static ValidationIssue error(String code, String fieldPath, String message) {
        return new ValidationIssue(ValidationSeverity.ERROR, code, fieldPath, message);
    }

    public static ValidationIssue warning(String code, String fieldPath, String message) {
        return new ValidationIssue(ValidationSeverity.WARNING, code, fieldPath, message);
    }
}
