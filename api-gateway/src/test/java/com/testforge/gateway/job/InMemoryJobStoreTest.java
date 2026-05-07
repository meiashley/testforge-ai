package com.testforge.gateway.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJobStoreTest {

    private InMemoryJobStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryJobStore();
    }

    @Test
    void saveReturnsNonNullJobId() {
        Job job = new Job();
        job.setStatus(JobStatus.PENDING);
        job.setPromptVersion("V3.1");
        job.setCreatedAt(Instant.now());

        String jobId = store.save(job);

        assertNotNull(jobId);
        assertFalse(jobId.isBlank());
    }

    @Test
    void findByIdReturnsPersistedJob() {
        Job job = new Job();
        job.setStatus(JobStatus.PENDING);
        job.setPromptVersion("V3.1");
        job.setOpenApiUrl("http://example.com/openapi.yaml");
        job.setCreatedAt(Instant.now());

        String jobId = store.save(job);

        Optional<Job> found = store.findById(jobId);
        assertTrue(found.isPresent());
        assertEquals(jobId, found.get().getJobId());
        assertEquals(JobStatus.PENDING, found.get().getStatus());
        assertEquals("V3.1", found.get().getPromptVersion());
    }

    @Test
    void updateReflectsNewValues() {
        Job job = new Job();
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(Instant.now());
        String jobId = store.save(job);

        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        store.update(job);

        Optional<Job> updated = store.findById(jobId);
        assertTrue(updated.isPresent());
        assertEquals(JobStatus.COMPLETED, updated.get().getStatus());
        assertNotNull(updated.get().getCompletedAt());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        Optional<Job> result = store.findById("does-not-exist");

        assertTrue(result.isEmpty());
    }
}
