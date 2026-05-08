package com.testforge.gateway.service;

import com.testforge.gateway.job.Job;
import com.testforge.gateway.job.JobStatus;
import com.testforge.gateway.job.JobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GenerationServiceTest {

    private JobStore jobStore;
    private GenerationExecutor generationExecutor;
    private GenerationService service;

    @BeforeEach
    void setUp() {
        jobStore = mock(JobStore.class);
        generationExecutor = mock(GenerationExecutor.class);
        when(jobStore.save(any())).thenReturn("job-uuid-001");
        service = new GenerationService(jobStore, generationExecutor);
    }

    @Test
    void submitJobCreatesPendingJobAndReturnsJobId() {
        String jobId = service.submitJob("http://example.com/openapi.yaml", "V3.1");

        assertEquals("job-uuid-001", jobId);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobStore).save(captor.capture());

        Job saved = captor.getValue();
        assertEquals(JobStatus.PENDING, saved.getStatus());
        assertEquals("V3.1", saved.getPromptVersion());
        assertEquals("http://example.com/openapi.yaml", saved.getOpenApiUrl());
        assertNotNull(saved.getCreatedAt());
        assertNull(saved.getCompletedAt());
        assertNull(saved.getReport());
    }

    @Test
    void submitJobDelegatesExecutionToGenerationExecutor() {
        service.submitJob("http://example.com/openapi.yaml", "V3.1");

        verify(generationExecutor).executeJobAsync("job-uuid-001", "http://example.com/openapi.yaml", "V3.1");
    }
}
