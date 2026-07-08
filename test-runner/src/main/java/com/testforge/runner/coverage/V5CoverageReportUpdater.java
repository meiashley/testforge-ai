package com.testforge.runner.coverage;

import com.testforge.runner.model.CoverageSummary;

import java.nio.file.Files;
import java.nio.file.Path;

public class V5CoverageReportUpdater {

    private static final String TARGET_MODULE = "mock-banking-api";
    private static final String DETAILS_PATH = "coverage/jacoco/index.html";

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.err.println("Usage: V5CoverageReportUpdater <report-json> <output-dir> <exec-data> "
                    + "<classes-dir> <sources-dir> <jacoco-output-dir>");
            System.exit(1);
        }

        Path reportJson = Path.of(args[0]).toAbsolutePath().normalize();
        Path outputDir = Path.of(args[1]).toAbsolutePath().normalize();
        Path execData = Path.of(args[2]).toAbsolutePath().normalize();
        Path classesDir = Path.of(args[3]).toAbsolutePath().normalize();
        Path sourcesDir = Path.of(args[4]).toAbsolutePath().normalize();
        Path jacocoOutputDir = Path.of(args[5]).toAbsolutePath().normalize();

        if (!Files.exists(reportJson)) {
            throw new IllegalStateException("V5 execution report JSON does not exist: " + reportJson);
        }

        CoverageSummary coverage;
        if (!Files.exists(execData)) {
            coverage = CoverageSummary.unavailable(TARGET_MODULE,
                    "JaCoCo exec data was not generated for this V5 run");
        } else {
            try {
                new JacocoReportGenerator().generate(execData, classesDir, sourcesDir, jacocoOutputDir,
                        "TestForge AI - Mock Banking API");
                Path xml = jacocoOutputDir.resolve("jacoco.xml");
                boolean detailsAvailable = Files.exists(jacocoOutputDir.resolve("index.html"));
                coverage = new JacocoCoverageParser().parse(
                        xml,
                        TARGET_MODULE,
                        detailsAvailable ? DETAILS_PATH : null,
                        execData);
            } catch (Exception e) {
                coverage = CoverageSummary.error(TARGET_MODULE,
                        "Failed to generate JaCoCo coverage report: " + e.getMessage());
                coverage.setExecDataPath(execData.toString());
                System.err.println("[coverage] " + coverage.getMessage());
            }
        }

        new CoverageReportEnricher().enrich(reportJson, outputDir, "v5", coverage);
        System.out.println("[coverage] V5 execution report updated with coverage status "
                + coverage.getStatus() + " at " + reportJson);
    }
}
