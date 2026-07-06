package com.testforge.ai.pipeline;

import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.validation.RejectedTestCase;
import com.testforge.ai.validation.ValidationIssue;
import lombok.Value;

import java.util.List;

/**
 * Immutable collection container for one generation run. The collection membership is
 * defensively copied; existing domain objects inside the collections are not deep-copied.
 */
@Value
public class TestGenerationOutcome {
    List<GenerationResult> generationResults;
    List<TestCase> acceptedTestCases;
    List<RejectedTestCase> rejectedTestCases;
    List<ValidationIssue> warnings;

    public TestGenerationOutcome(List<GenerationResult> generationResults,
                                 List<TestCase> acceptedTestCases,
                                 List<RejectedTestCase> rejectedTestCases,
                                 List<ValidationIssue> warnings) {
        this.generationResults = List.copyOf(generationResults);
        this.acceptedTestCases = List.copyOf(acceptedTestCases);
        this.rejectedTestCases = List.copyOf(rejectedTestCases);
        this.warnings = List.copyOf(warnings);
    }
}
