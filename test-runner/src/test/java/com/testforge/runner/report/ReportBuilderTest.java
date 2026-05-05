package com.testforge.runner.report;

import com.testforge.ai.model.Priority;
import com.testforge.ai.model.TestCaseType;
import com.testforge.runner.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportBuilderTest {

    private final ReportBuilder builder = new ReportBuilder();

    @Test
    void failedWithPayPrefix_categorizedAsHardcodedDynamicValue() {
        AssertionResult ar = new AssertionResult("id", "pay_fixed123", "pay_actual", false,
                "expected pay_fixed123 but was pay_actual");
        TestCaseResult result = failedResult("tc-1",
                new HttpResponse(201, Map.of("id", "pay_actual"), "{}", Map.of(), 10),
                List.of(ar));

        ExecutionReport report = builder.build(List.of(result));

        assertEquals("Hardcoded dynamic value", report.getResults().get(0).getFailureCategory());
    }

    @Test
    void failedWith404_categorizedAsResourceNotFound() {
        AssertionResult ar = new AssertionResult("status", "COMPLETED", null, false, "field absent");
        TestCaseResult result = failedResult("tc-2",
                new HttpResponse(404, null, "Not Found", Map.of(), 10),
                List.of(ar));

        ExecutionReport report = builder.build(List.of(result));

        assertEquals("Resource not found (likely hardcoded id)", report.getResults().get(0).getFailureCategory());
    }

    @Test
    void failedWith2xxButStatusExpected4xx_categorizedAsValidationExpectationIncorrect() {
        TestCaseResult result = failedResult("tc-3",
                new HttpResponse(201, Map.of("id", "pay_abc"), "{}", Map.of(), 10),
                List.of());

        ExecutionReport report = builder.build(List.of(result));

        assertEquals("Validation expectation incorrect", report.getResults().get(0).getFailureCategory());
    }

    @Test
    void failedWithTypeMismatch_categorizedAsTypeMismatch() {
        AssertionResult ar = new AssertionResult("amount", "100", 100, false,
                "type mismatch: expected String but was Integer");
        TestCaseResult result = failedResult("tc-4",
                new HttpResponse(400, null, "", Map.of(), 10),
                List.of(ar));

        ExecutionReport report = builder.build(List.of(result));

        assertEquals("Type mismatch", report.getResults().get(0).getFailureCategory());
    }

    private TestCaseResult failedResult(String id, HttpResponse httpResponse, List<AssertionResult> assertions) {
        return new TestCaseResult(id, "test-" + id, TestCaseType.HAPPY_PATH, Priority.P0,
                TestResultStatus.FAILED, httpResponse, assertions, null, httpResponse.getDurationMs());
    }
}
