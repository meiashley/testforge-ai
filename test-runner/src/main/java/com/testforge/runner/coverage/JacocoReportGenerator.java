package com.testforge.runner.coverage;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.MultiReportVisitor;
import org.jacoco.report.csv.CSVFormatter;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class JacocoReportGenerator {

    public void generate(Path execDataPath, Path classesDir, Path sourcesDir, Path outputDir, String reportName)
            throws IOException {
        if (!Files.exists(execDataPath)) {
            throw new IOException("JaCoCo exec data does not exist: " + execDataPath);
        }
        if (!Files.isDirectory(classesDir)) {
            throw new IOException("Target classes directory does not exist: " + classesDir);
        }
        if (!Files.isDirectory(sourcesDir)) {
            throw new IOException("Target sources directory does not exist: " + sourcesDir);
        }

        deleteRecursively(outputDir);
        Files.createDirectories(outputDir);

        ExecFileLoader loader = new ExecFileLoader();
        loader.load(execDataPath.toFile());

        CoverageBuilder coverageBuilder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(loader.getExecutionDataStore(), coverageBuilder);
        analyzer.analyzeAll(classesDir.toFile());

        HTMLFormatter htmlFormatter = new HTMLFormatter();
        XMLFormatter xmlFormatter = new XMLFormatter();
        CSVFormatter csvFormatter = new CSVFormatter();

        IReportVisitor visitor = new MultiReportVisitor(List.of(
                htmlFormatter.createVisitor(new FileMultiReportOutput(outputDir.toFile())),
                xmlFormatter.createVisitor(Files.newOutputStream(outputDir.resolve("jacoco.xml"))),
                csvFormatter.createVisitor(Files.newOutputStream(outputDir.resolve("jacoco.csv")))
        ));

        visitor.visitInfo(loader.getSessionInfoStore().getInfos(), loader.getExecutionDataStore().getContents());
        visitor.visitBundle(coverageBuilder.getBundle(reportName),
                new DirectorySourceFileLocator(sourcesDir.toFile(), "UTF-8", 4));
        visitor.visitEnd();
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path current : paths.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}
