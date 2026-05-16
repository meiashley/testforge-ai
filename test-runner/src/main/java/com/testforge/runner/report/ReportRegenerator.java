package com.testforge.runner.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.testforge.runner.model.ExecutionReport;

import java.nio.file.Path;

public class ReportRegenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: ReportRegenerator <report-json-path> [output-dir]");
            System.exit(1);
        }

        Path jsonPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length == 2
                ? Path.of(args[1]).toAbsolutePath().normalize()
                : jsonPath.getParent();

        String fileName = jsonPath.getFileName().toString();
        String suffix = "-execution-report.json";
        if (!fileName.endsWith(suffix)) {
            throw new IllegalArgumentException("Input file must end with " + suffix + ": " + fileName);
        }

        String prefix = fileName.substring(0, fileName.length() - suffix.length());
        ExecutionReport report = MAPPER.readValue(jsonPath.toFile(), ExecutionReport.class);

        new ReportWriter(prefix).write(report, outputDir);
        System.out.println("[report-regenerator] regenerated reports for prefix '" + prefix + "' in " + outputDir);
    }
}
