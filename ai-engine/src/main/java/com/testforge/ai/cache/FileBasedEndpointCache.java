package com.testforge.ai.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.model.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class FileBasedEndpointCache implements EndpointCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<TestCase>> TEST_CASE_LIST = new TypeReference<>() {};

    private final Path cacheDir;

    public FileBasedEndpointCache() {
        this(Path.of(System.getProperty("user.home"), ".testforge-cache"));
    }

    public FileBasedEndpointCache(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    @Override
    public Optional<List<TestCase>> findByFingerprint(String fingerprint) {
        Path file = cacheDir.resolve(fingerprint + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            List<TestCase> testCases = MAPPER.readValue(file.toFile(), TEST_CASE_LIST);
            return Optional.of(testCases);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void save(String fingerprint, List<TestCase> testCases) {
        try {
            Files.createDirectories(cacheDir);
            Path file = cacheDir.resolve(fingerprint + ".json");
            MAPPER.writeValue(file.toFile(), testCases);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write cache entry: " + fingerprint, e);
        }
    }
}
