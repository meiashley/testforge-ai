package com.testforge.gateway.service;

import com.testforge.gateway.job.Job;
import com.testforge.gateway.job.JobStatus;
import com.testforge.gateway.job.JobStore;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class GenerationService {

    private final JobStore jobStore;
    private final GenerationExecutor generationExecutor;

    public GenerationService(JobStore jobStore, GenerationExecutor generationExecutor) {
        this.jobStore = jobStore;
        this.generationExecutor = generationExecutor;
    }

    public String submitJob(String openApiUrl, String promptVersion) {
        Job job = new Job();
        job.setStatus(JobStatus.PENDING);
        job.setPromptVersion(promptVersion);
        job.setOpenApiUrl(openApiUrl);
        job.setCreatedAt(Instant.now());
        String jobId = jobStore.save(job);

        generationExecutor.executeJobAsync(jobId, openApiUrl, promptVersion);

        return jobId;
    }
}
