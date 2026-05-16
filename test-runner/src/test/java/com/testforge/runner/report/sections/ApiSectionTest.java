package com.testforge.runner.report.sections;

import com.testforge.ai.model.TestCaseType;
import com.testforge.runner.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApiSectionTest {

    private final ApiSection section = new ApiSection();

    @Test
    void render_containsTestCaseNameAndStatus() {
        ExecutionSummary s = new ExecutionSummary(1, 1, 0, 0, 1.0, "2026-05-16T00:00:00Z", 50);
        HttpResponse resp = new HttpResponse(201, Map.of("id", "pay-1"), "{}", Map.of(), 30L);
        AssertionResult ar = new AssertionResult("status", 201, 201, true, null);
        TestCaseResult tc = new TestCaseResult(
                "tc-1", "createPayment_happy", TestCaseType.HAPPY_PATH, null,
                TestResultStatus.PASSED, null, resp, List.of(ar), null, 50);

        ExecutionReport report = new ExecutionReport(s, List.of(tc));
        String html = section.render(report);

        assertTrue(html.contains("createPayment_happy"));
        assertTrue(html.contains("HAPPY_PATH"));
        assertTrue(section.hasContent(report));
    }
}
