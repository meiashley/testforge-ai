package com.testforge.runner.report;

import com.testforge.ai.model.TestCaseType;
import com.testforge.runner.model.AssertionResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import com.testforge.runner.model.HttpResponse;
import com.testforge.runner.model.TestCaseResult;
import com.testforge.runner.model.TestResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HtmlReportGeneratorTest {

    private HtmlReportGenerator generator;
    private ExecutionReport report;

    @BeforeEach
    void setUp() {
        generator = new HtmlReportGenerator();

        ExecutionSummary summary = new ExecutionSummary(2, 1, 1, 0, 50.0, "2026-05-08T04:00:00Z", 120);

        HttpResponse response = new HttpResponse(200, Map.of("id", "1"), "{\"id\":\"1\"}", Map.of(), 60);

        AssertionResult passed = new AssertionResult("status", 200, 200, true, null);
        AssertionResult failed = new AssertionResult("body.id", "abc", "xyz", false, "mismatch");

        TestCaseResult tc1 = new TestCaseResult(
                "tc-1", "createPayment_happy", TestCaseType.HAPPY_PATH, null,
                TestResultStatus.PASSED, response, List.of(passed), null, 60);

        TestCaseResult tc2 = new TestCaseResult(
                "tc-2", "createPayment_negative", TestCaseType.NEGATIVE, null,
                TestResultStatus.FAILED, response, List.of(passed, failed), "ASSERTION_FAILURE", 60);

        report = new ExecutionReport(summary, List.of(tc1, tc2));
    }

    @Test
    void outputContainsTitle() {
        String html = generator.generate(report);
        assertTrue(html.contains("TestForge AI Execution Report"));
    }

    @Test
    void outputContainsPassAndTotalNumbers() {
        String html = generator.generate(report);
        assertTrue(html.contains(">1<"), "should contain passed count 1");
        assertTrue(html.contains(">2<"), "should contain total count 2");
    }

    @Test
    void outputContainsEndpointName() {
        String html = generator.generate(report);
        assertTrue(html.contains("createPayment"));
    }

    @Test
    void outputIsSelfContainedNoExternalUrls() {
        String html = generator.generate(report);
        assertFalse(html.contains("https://"), "should not reference external https URLs");
        assertFalse(html.contains("http://"),  "should not reference external http URLs");
    }
}
