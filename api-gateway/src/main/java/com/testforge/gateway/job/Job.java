package com.testforge.gateway.job;

import com.testforge.runner.model.ExecutionReport;
import lombok.Data;

import java.time.Instant;

@Data
public class Job {
    private String jobId;
    private JobStatus status;
    private String promptVersion;
    private String openApiUrl;
    private Instant createdAt;
    private Instant completedAt;
    private ExecutionReport report;
    private String errorMessage;
}
