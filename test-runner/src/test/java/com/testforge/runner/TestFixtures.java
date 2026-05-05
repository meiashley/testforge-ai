package com.testforge.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestFixtures {

    public static String loadMockBankingApiSpec() throws IOException {
        String basedir = System.getProperty("project.basedir", ".");
        Path specPath = Path.of(basedir)
                .resolve("../mock-banking-api/src/main/resources/static/openapi.yaml")
                .normalize();
        return Files.readString(specPath);
    }
}
