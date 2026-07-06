package com.testforge.gateway.job;

import com.testforge.runner.model.ExecutionReport;
import com.testforge.ai.model.GenerationResult;
import com.testforge.ai.validation.RejectedTestCase;
import com.testforge.ai.validation.ValidationIssue;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class Job {
    private String jobId;
    private JobStatus status;
    private String promptVersion;
    private String openApiUrl;
    private Instant createdAt;
    private Instant completedAt;
    private ExecutionReport report;
    private List<GenerationResult> generationResults = List.of();
    private List<RejectedTestCase> rejectedTestCases = List.of();
    private List<ValidationIssue> validationWarnings = List.of();
    private boolean partialAcceptance;
    private String errorMessage;

    public void setGenerationResults(List<GenerationResult> generationResults) {
        this.generationResults = generationResults == null ? List.of() : List.copyOf(generationResults);
    }

    public void setRejectedTestCases(List<RejectedTestCase> rejectedTestCases) {
        this.rejectedTestCases = rejectedTestCases == null ? List.of() : List.copyOf(rejectedTestCases);
    }

    public void setValidationWarnings(List<ValidationIssue> validationWarnings) {
        this.validationWarnings = validationWarnings == null ? List.of() : List.copyOf(validationWarnings);
    }
}
