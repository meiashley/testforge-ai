package com.testforge.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.gateway.job.Job;
import com.testforge.gateway.job.JobStatus;
import com.testforge.gateway.job.JobStore;
import com.testforge.gateway.service.GenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GenerationService generationService;

    @MockBean
    private JobStore jobStore;

    @Test
    void postValidRequest_returns202WithJobId() throws Exception {
        when(generationService.submitJob("http://example.com/api.yaml", "V3.1"))
                .thenReturn("job-abc-123");

        GenerateTestsRequest request = new GenerateTestsRequest();
        request.setOpenApiUrl("http://example.com/api.yaml");
        request.setPromptVersion("V3.1");

        mockMvc.perform(post("/api/v1/generate-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-abc-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.statusUrl").value("/api/v1/jobs/job-abc-123"));
    }

    @Test
    void postMissingOpenApiUrl_returns400() throws Exception {
        GenerateTestsRequest request = new GenerateTestsRequest();
        request.setOpenApiUrl("");

        mockMvc.perform(post("/api/v1/generate-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void postUnsupportedPromptVersion_returns400() throws Exception {
        when(generationService.submitJob("http://example.com/api.yaml", "V99"))
                .thenThrow(new IllegalArgumentException("Unknown promptVersion: V99"));

        GenerateTestsRequest request = new GenerateTestsRequest();
        request.setOpenApiUrl("http://example.com/api.yaml");
        request.setPromptVersion("V99");

        mockMvc.perform(post("/api/v1/generate-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown promptVersion: V99"));
    }

    @Test
    void getExistingJob_returns200WithJobJson() throws Exception {
        Job job = new Job();
        job.setJobId("job-xyz-456");
        job.setStatus(JobStatus.RUNNING);
        job.setOpenApiUrl("http://example.com/api.yaml");
        job.setPromptVersion("V3.1");
        job.setCreatedAt(Instant.parse("2026-05-07T10:00:00Z"));

        when(jobStore.findById("job-xyz-456")).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/jobs/job-xyz-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-xyz-456"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.openApiUrl").value("http://example.com/api.yaml"));
    }

    @Test
    void getNonExistentJob_returns404() throws Exception {
        when(jobStore.findById("no-such-job")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jobs/no-such-job"))
                .andExpect(status().isNotFound());
    }
}
