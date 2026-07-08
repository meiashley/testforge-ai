package com.testforge.runner.coverage;

import com.testforge.runner.model.CoverageMetric;
import com.testforge.runner.model.CoverageStatus;
import com.testforge.runner.model.CoverageSummary;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

public class JacocoCoverageParser {

    private static final Pattern JACOCO_REPORT_DOCTYPE = Pattern.compile(
            "<!DOCTYPE\\s+report\\s+PUBLIC\\s+\"-//JACOCO//DTD Report 1\\.1//EN\"\\s+\"report\\.dtd\"\\s*>");

    private enum CounterType {
        LINE,
        BRANCH,
        INSTRUCTION,
        METHOD,
        CLASS,
        COMPLEXITY
    }

    public CoverageSummary parse(Path xmlPath, String targetModule, String detailsPath, Path execDataPath) {
        if (xmlPath == null || !Files.exists(xmlPath)) {
            return CoverageSummary.unavailable(targetModule, "JaCoCo XML report was not generated");
        }
        try {
            DocumentBuilderFactory factory = secureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(readSafeJacocoXml(xmlPath))));
            Element report = document.getDocumentElement();
            if (report == null || !"report".equals(report.getNodeName())) {
                return CoverageSummary.error(targetModule, "JaCoCo XML root element must be <report>");
            }

            Map<CounterType, CoverageMetric> metrics = readTopLevelCounters(report);
            for (CounterType type : CounterType.values()) {
                if (!metrics.containsKey(type)) {
                    return CoverageSummary.error(targetModule, "JaCoCo XML is missing top-level " + type + " counter");
                }
            }

            CoverageSummary summary = new CoverageSummary();
            summary.setStatus(CoverageStatus.AVAILABLE);
            summary.setTargetModule(targetModule);
            summary.setLine(metrics.get(CounterType.LINE));
            summary.setBranch(metrics.get(CounterType.BRANCH));
            summary.setInstruction(metrics.get(CounterType.INSTRUCTION));
            summary.setMethod(metrics.get(CounterType.METHOD));
            summary.setClazz(metrics.get(CounterType.CLASS));
            summary.setComplexity(metrics.get(CounterType.COMPLEXITY));
            summary.setDetailsPath(detailsPath);
            summary.setGeneratedAt(Instant.now().toString());
            summary.setMessage("Coverage data generated from JaCoCo XML");
            summary.setExecDataPath(execDataPath != null ? execDataPath.toString() : null);
            return summary;
        } catch (ParserConfigurationException | SAXException | IOException | IllegalArgumentException e) {
            return CoverageSummary.error(targetModule, "Failed to parse JaCoCo XML: " + e.getMessage());
        }
    }

    private String readSafeJacocoXml(Path xmlPath) throws IOException {
        String xml = Files.readString(xmlPath, StandardCharsets.UTF_8);
        xml = JACOCO_REPORT_DOCTYPE.matcher(xml).replaceFirst("");
        if (xml.contains("<!DOCTYPE")) {
            throw new IllegalArgumentException("Unsupported DOCTYPE in JaCoCo XML");
        }
        return xml;
    }

    private Map<CounterType, CoverageMetric> readTopLevelCounters(Element report) {
        Map<CounterType, CoverageMetric> metrics = new EnumMap<>(CounterType.class);
        NodeList children = report.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && "counter".equals(element.getNodeName())) {
                CounterType type = CounterType.valueOf(element.getAttribute("type"));
                int missed = Integer.parseInt(element.getAttribute("missed"));
                int covered = Integer.parseInt(element.getAttribute("covered"));
                metrics.put(type, CoverageMetric.of(covered, missed));
            }
        }
        return metrics;
    }

    private DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }
}
