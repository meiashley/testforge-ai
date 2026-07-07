package com.testforge.ai.validation;

public class StructuralValidationException extends RuntimeException {
    private final TestCaseStructuralValidationResult result;

    public StructuralValidationException(String message, TestCaseStructuralValidationResult result) {
        super(message);
        this.result = result;
    }

    public TestCaseStructuralValidationResult getResult() {
        return result;
    }
}
