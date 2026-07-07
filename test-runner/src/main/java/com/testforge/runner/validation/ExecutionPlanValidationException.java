package com.testforge.runner.validation;

public class ExecutionPlanValidationException extends RuntimeException {
    private final ExecutionPlanValidationResult result;

    public ExecutionPlanValidationException(String message, ExecutionPlanValidationResult result) {
        super(message);
        this.result = result;
    }

    public ExecutionPlanValidationResult getResult() {
        return result;
    }
}
