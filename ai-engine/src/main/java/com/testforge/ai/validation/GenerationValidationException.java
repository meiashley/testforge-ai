package com.testforge.ai.validation;

import com.testforge.ai.pipeline.TestGenerationOutcome;

public class GenerationValidationException extends RuntimeException {
    private final TestGenerationOutcome outcome;

    public GenerationValidationException(String message, TestGenerationOutcome outcome) {
        super(message);
        this.outcome = outcome;
    }

    public TestGenerationOutcome getOutcome() {
        return outcome;
    }
}
