package com.testforge.gateway.job;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryJobStore implements JobStore {

    private final ConcurrentHashMap<String, Job> store = new ConcurrentHashMap<>();

    @Override
    public String save(Job job) {
        if (job.getJobId() == null) {
            job.setJobId(UUID.randomUUID().toString());
        }
        store.put(job.getJobId(), job);
        return job.getJobId();
    }

    @Override
    public Optional<Job> findById(String jobId) {
        return Optional.ofNullable(store.get(jobId));
    }

    @Override
    public void update(Job job) {
        store.put(job.getJobId(), job);
    }
}
