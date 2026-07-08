package com.testforge.runner.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.testforge.runner.model.CoverageSummary;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.report.ReportWriter;

import java.nio.file.Path;

public class CoverageReportEnricher {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new ParameterNamesModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void enrich(Path reportJsonPath, Path outputDir, String prefix, CoverageSummary coverage) throws Exception {
        ExecutionReport report = MAPPER.readValue(reportJsonPath.toFile(), ExecutionReport.class);
        report.setCoverage(coverage);
        new ReportWriter(prefix).write(report, outputDir);
    }
}
