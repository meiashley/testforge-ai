package com.testforge.gateway.controller;

import com.testforge.gateway.job.Job;
import com.testforge.gateway.job.JobStore;
import com.testforge.gateway.service.GenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class JobController {

    private final GenerationService generationService;
    private final JobStore jobStore;

    public JobController(GenerationService generationService, JobStore jobStore) {
        this.generationService = generationService;
        this.jobStore = jobStore;
    }

    @PostMapping("/generate-tests")
    public ResponseEntity<GenerateTestsResponse> generateTests(
            @Valid @RequestBody GenerateTestsRequest request) {

        String promptVersion = request.getPromptVersion() == null ? "V3.1" : request.getPromptVersion();
        String jobId = generationService.submitJob(request.getOpenApiUrl(), promptVersion);

        GenerateTestsResponse response = new GenerateTestsResponse(
                jobId,
                "PENDING",
                "/api/v1/jobs/" + jobId
        );
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<Job> getJob(@PathVariable String jobId) {
        return jobStore.findById(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
