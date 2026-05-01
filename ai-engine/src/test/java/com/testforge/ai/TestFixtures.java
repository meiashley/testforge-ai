package com.testforge.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestFixtures {

    /**
     * Loads the real openapi.yaml from mock-banking-api.
     * Maven Surefire injects ${project.basedir} (= ai-engine/ absolute path).
     * Fallback "." works when running from IDE with working dir = ai-engine/.
     */
    public static String loadMockBankingApiSpec() throws IOException {
        String basedir = System.getProperty("project.basedir", ".");
        Path specPath = Path.of(basedir)
                .resolve("../mock-banking-api/src/main/resources/static/openapi.yaml")
                .normalize();
        return Files.readString(specPath);
    }
}
