package com.testforge.runner.report.sections;

import com.testforge.ai.scenario.Assertion;
import com.testforge.ai.scenario.ScenarioStep;
import com.testforge.runner.execution.AssertionResult;
import com.testforge.runner.execution.PlanExecutionResult;
import com.testforge.runner.execution.StepResult;
import com.testforge.runner.model.ExecutionReport;
import com.testforge.runner.model.ExecutionSummary;
import com.testforge.runner.model.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioSectionTest {

    private final ScenarioSection section = new ScenarioSection();

    private ExecutionReport emptyReport() {
        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        return new ExecutionReport(s, List.of());
    }

    private ExecutionReport reportWithScenario() {
        ScenarioStep step = ScenarioStep.builder()
                .order(0).stepId("step-1").role("creator")
                .method("POST").pathTemplate("/api/payments")
                .pathBindings(Map.of()).headerBindings(Map.of()).outputCapture(Map.of())
                .expectedStatusCode(201).assertions(List.of())
                .stepDescription("User creates a payment")
                .build();

        Assertion assertion = new Assertion("$.body.status", "EQUALS", "COMPLETED");
        AssertionResult assertionResult = AssertionResult.builder()
                .assertion(assertion).passed(true).actualValue("COMPLETED").build();

        HttpResponse response = new HttpResponse(201, Map.of("id", "pay-001", "status", "COMPLETED"),
                "{}", Map.of(), 50L);
        StepResult sr = StepResult.builder()
                .step(step).response(response).statusMatch(true)
                .assertionResults(List.of(assertionResult)).passed(true).build();

        PlanExecutionResult plan = PlanExecutionResult.builder()
                .planId("plan-1").scenarioId("sc-1").scenarioName("Happy Path Create")
                .steps(List.of(sr)).passed(true).build();

        ExecutionSummary s = new ExecutionSummary(0, 0, 0, 0, 0.0, "2026-05-16T00:00:00Z", 0);
        ExecutionReport report = new ExecutionReport(s, List.of());
        report.setScenarioResults(List.of(plan));
        return report;
    }

    @Test
    void hasContent_falseWhenNoScenarios() {
        assertFalse(section.hasContent(emptyReport()));
    }

    @Test
    void hasContent_trueWhenScenariosPresent() {
        assertTrue(section.hasContent(reportWithScenario()));
    }

    @Test
    void render_containsScenarioNameAndStep() {
        String html = section.render(reportWithScenario());
        assertTrue(html.contains("Happy Path Create"));
        assertTrue(html.contains("POST"));
        assertTrue(html.contains("/api/payments"));
        assertTrue(html.contains("creator"));
    }
}
