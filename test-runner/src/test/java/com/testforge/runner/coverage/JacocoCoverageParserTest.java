package com.testforge.runner.coverage;

import com.testforge.runner.model.CoverageStatus;
import com.testforge.runner.model.CoverageSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JacocoCoverageParserTest {

    @TempDir
    Path tempDir;

    private final JacocoCoverageParser parser = new JacocoCoverageParser();

    @Test
    void parsesTopLevelCountersAndPercentages() throws Exception {
        Path xml = write("jacoco.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <report name="mock">
                  <package name="ignored">
                    <counter type="LINE" missed="999" covered="1"/>
                  </package>
                  <counter type="INSTRUCTION" missed="27" covered="316"/>
                  <counter type="BRANCH" missed="1" covered="9"/>
                  <counter type="LINE" missed="10" covered="105"/>
                  <counter type="COMPLEXITY" missed="3" covered="22"/>
                  <counter type="METHOD" missed="2" covered="18"/>
                  <counter type="CLASS" missed="0" covered="8"/>
                </report>
                """);

        CoverageSummary summary = parser.parse(xml, "mock-banking-api",
                "coverage/jacoco/index.html", tempDir.resolve("jacoco-v5.exec"));

        assertEquals(CoverageStatus.AVAILABLE, summary.getStatus());
        assertEquals("mock-banking-api", summary.getTargetModule());
        assertEquals(105, summary.getLine().getCovered());
        assertEquals(10, summary.getLine().getMissed());
        assertEquals(115, summary.getLine().getTotal());
        assertEquals(10500.0 / 115, summary.getLine().getPercentage(), 0.0001);
        assertEquals(9, summary.getBranch().getCovered());
        assertEquals(1, summary.getBranch().getMissed());
        assertEquals(316, summary.getInstruction().getCovered());
        assertEquals(18, summary.getMethod().getCovered());
        assertEquals(8, summary.getClazz().getCovered());
        assertEquals(22, summary.getComplexity().getCovered());
    }

    @Test
    void acceptsJacocoReportDoctypeWithoutLoadingExternalDtd() throws Exception {
        Path xml = write("jacoco-doctype.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
                <report name="mock">
                  <counter type="INSTRUCTION" missed="27" covered="316"/>
                  <counter type="BRANCH" missed="1" covered="9"/>
                  <counter type="LINE" missed="10" covered="105"/>
                  <counter type="COMPLEXITY" missed="3" covered="22"/>
                  <counter type="METHOD" missed="2" covered="18"/>
                  <counter type="CLASS" missed="0" covered="8"/>
                </report>
                """);

        CoverageSummary summary = parser.parse(xml, "mock-banking-api",
                "coverage/jacoco/index.html", tempDir.resolve("jacoco-v5.exec"));

        assertEquals(CoverageStatus.AVAILABLE, summary.getStatus());
        assertEquals(105, summary.getLine().getCovered());
    }

    @Test
    void missingXml_returnsUnavailable() {
        CoverageSummary summary = parser.parse(tempDir.resolve("missing.xml"), "mock-banking-api",
                "coverage/jacoco/index.html", null);

        assertEquals(CoverageStatus.UNAVAILABLE, summary.getStatus());
        assertNull(summary.getLine());
    }

    @Test
    void missingCounter_returnsError() throws Exception {
        Path xml = write("missing-counter.xml", """
                <report name="mock">
                  <counter type="LINE" missed="1" covered="1"/>
                </report>
                """);

        CoverageSummary summary = parser.parse(xml, "mock-banking-api", null, null);

        assertEquals(CoverageStatus.ERROR, summary.getStatus());
        assertTrue(summary.getMessage().contains("missing top-level"));
    }

    @Test
    void zeroTotalCounter_returnsError() throws Exception {
        Path xml = write("zero.xml", """
                <report name="mock">
                  <counter type="INSTRUCTION" missed="0" covered="0"/>
                  <counter type="BRANCH" missed="1" covered="9"/>
                  <counter type="LINE" missed="10" covered="105"/>
                  <counter type="COMPLEXITY" missed="3" covered="22"/>
                  <counter type="METHOD" missed="2" covered="18"/>
                  <counter type="CLASS" missed="0" covered="8"/>
                </report>
                """);

        CoverageSummary summary = parser.parse(xml, "mock-banking-api", null, null);

        assertEquals(CoverageStatus.ERROR, summary.getStatus());
        assertTrue(summary.getMessage().contains("total"));
    }

    @Test
    void malformedXml_returnsError() throws Exception {
        Path xml = write("broken.xml", "<report><counter></report>");

        CoverageSummary summary = parser.parse(xml, "mock-banking-api", null, null);

        assertEquals(CoverageStatus.ERROR, summary.getStatus());
    }

    @Test
    void doctypeIsRejected() throws Exception {
        Path xml = write("xxe.xml", """
                <?xml version="1.0"?>
                <!DOCTYPE report [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <report name="&xxe;">
                  <counter type="INSTRUCTION" missed="1" covered="1"/>
                  <counter type="BRANCH" missed="1" covered="1"/>
                  <counter type="LINE" missed="1" covered="1"/>
                  <counter type="COMPLEXITY" missed="1" covered="1"/>
                  <counter type="METHOD" missed="1" covered="1"/>
                  <counter type="CLASS" missed="1" covered="1"/>
                </report>
                """);

        CoverageSummary summary = parser.parse(xml, "mock-banking-api", null, null);

        assertEquals(CoverageStatus.ERROR, summary.getStatus());
    }

    private Path write(String fileName, String content) throws Exception {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content);
        return path;
    }
}
