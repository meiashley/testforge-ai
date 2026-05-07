package com.testforge.gateway.job;

import java.util.Optional;

public interface JobStore {
    String save(Job job);
    Optional<Job> findById(String jobId);
    void update(Job job);
}
