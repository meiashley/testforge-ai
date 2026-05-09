package com.testforge.ai.cache;

import com.testforge.ai.model.TestCase;

import java.util.List;
import java.util.Optional;

public interface EndpointCache {
    Optional<List<TestCase>> findByFingerprint(String fingerprint);
    void save(String fingerprint, List<TestCase> testCases);
}
