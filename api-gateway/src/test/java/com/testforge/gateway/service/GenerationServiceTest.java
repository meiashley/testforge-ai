package com.testforge.gateway.service;

import com.testforge.ai.prompt.PromptBuilder;
import com.testforge.ai.prompt.PromptBuilderV2;
import com.testforge.ai.prompt.PromptBuilderV3;
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
    private GenerationService service;

    @BeforeEach
    void setUp() {
        jobStore = mock(JobStore.class);
        when(jobStore.save(any())).thenReturn("job-uuid-001");
        service = new GenerationService(jobStore);
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
    void resolvePromptBuilderReturnsV1() {
        assertInstanceOf(PromptBuilder.class, service.resolvePromptBuilder("V1"));
    }

    @Test
    void resolvePromptBuilderReturnsV2() {
        assertInstanceOf(PromptBuilderV2.class, service.resolvePromptBuilder("V2"));
    }

    @Test
    void resolvePromptBuilderReturnsV3ForV3() {
        assertInstanceOf(PromptBuilderV3.class, service.resolvePromptBuilder("V3"));
    }

    @Test
    void resolvePromptBuilderReturnsV3ForV31() {
        assertInstanceOf(PromptBuilderV3.class, service.resolvePromptBuilder("V3.1"));
    }

    @Test
    void resolvePromptBuilderThrowsForUnknownVersion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.resolvePromptBuilder("V99"));
        assertTrue(ex.getMessage().contains("V99"));
    }
}
