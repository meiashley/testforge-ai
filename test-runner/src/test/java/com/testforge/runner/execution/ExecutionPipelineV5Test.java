package com.testforge.runner.execution;

import com.testforge.ai.scenario.*;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.HttpResponse;
import com.testforge.runner.pipeline.ExecutionPipeline;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutionPipelineV5Test {

    private HttpExecutor httpExecutor;
    private ExecutionPipeline pipeline;

    @BeforeEach
    void setUp() {
        httpExecutor = mock(HttpExecutor.class);
        pipeline = new ExecutionPipeline(
                httpExecutor,
                new AssertionEvaluator(),
                mock(ReportBuilder.class),
                mock(ReportWriter.class)
        );
    }

    private ScenarioStep step(int order, String id, String method, String pathTemplate,
                               Map<String, String> pathBindings, Map<String, String> outputCapture,
                               int expectedStatus, List<Assertion> assertions) {
        return ScenarioStep.builder()
                .order(order)
                .stepId(id)
                .role("creator")
                .method(method)
                .pathTemplate(pathTemplate)
                .pathBindings(pathBindings != null ? pathBindings : Map.of())
                .headerBindings(Map.of())
                .outputCapture(outputCapture != null ? outputCapture : Map.of())
                .expectedStatusCode(expectedStatus)
                .assertions(assertions != null ? assertions : List.of())
                .build();
    }

    private HttpResponse httpResponse(int status, Map<String, Object> body) {
        return new HttpResponse(status, body, "", Map.of(), 10L);
    }

    private ExecutionPlan plan(String planId, List<ScenarioStep> steps) {
        return ExecutionPlan.builder()
                .planId(planId)
                .source("scenario")
                .scenarioId("sc-test")
                .scenarioName("Test Scenario")
                .steps(steps)
                .build();
    }

    @Test
    void executePlan_singleStepSuccess_allPass() {
        ScenarioStep step = step(0, "step-1", "POST", "/api/payments",
                null,
                Map.of("payment.id", "$.body.id"),
                201,
                List.of(new Assertion("$.body.status", "EQUALS", "COMPLETED")));

        when(httpExecutor.execute(eq("POST"), contains("/api/payments"), any(), any()))
                .thenReturn(httpResponse(201, Map.of("id", "pay-001", "status", "COMPLETED")));

        PlanExecutionResult result = pipeline.executePlan(plan("plan-1", List.of(step)), "http://localhost:8080");

        assertTrue(result.isPassed());
        assertEquals(1, result.getSteps().size());
        StepResult sr = result.getSteps().get(0);
        assertTrue(sr.isPassed());
        assertTrue(sr.isStatusMatch());
        assertEquals(1, sr.getAssertionResults().size());
        assertTrue(sr.getAssertionResults().get(0).isPassed());
        assertNull(sr.getSkipReason());
    }

    @Test
    void executePlan_multiStepWithBinding_step2PathResolved() {
        // step1: POST /api/payments → captures payment.id from body
        ScenarioStep step1 = step(0, "step-1", "POST", "/api/payments",
                null,
                Map.of("payment.id", "$.body.id"),
                201, List.of());

        // step2: POST /api/payments/{id}/refund — {id} bound from ${payment.id}
        ScenarioStep step2 = step(1, "step-2", "POST", "/api/payments/{id}/refund",
                Map.of("id", "${payment.id}"),
                Map.of(),
                200, List.of());

        when(httpExecutor.execute(eq("POST"), contains("/api/payments"), any(), any()))
                .thenReturn(httpResponse(201, Map.of("id", "pay-abc", "status", "COMPLETED")));
        when(httpExecutor.execute(eq("POST"), contains("/api/payments/pay-abc/refund"), any(), any()))
                .thenReturn(httpResponse(200, Map.of("status", "REFUNDED")));

        PlanExecutionResult result = pipeline.executePlan(
                plan("plan-2", List.of(step1, step2)), "http://localhost:8080");

        assertTrue(result.isPassed());
        assertEquals(2, result.getSteps().size());
        assertTrue(result.getSteps().get(0).isPassed());
        assertTrue(result.getSteps().get(1).isPassed());

        // Verify step2 was called with resolved path
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpExecutor, times(2)).execute(eq("POST"), urlCaptor.capture(), any(), any());
        String step2Url = urlCaptor.getAllValues().get(1);
        assertTrue(step2Url.contains("/api/payments/pay-abc/refund"),
                "Step2 URL should have resolved ${payment.id} to pay-abc but was: " + step2Url);
    }

    @Test
    void executePlan_earlyTermination_step2Skipped() {
        ScenarioStep step1 = step(0, "step-1", "POST", "/api/payments",
                null, null, 201, List.of());
        ScenarioStep step2 = step(1, "step-2", "GET", "/api/payments/{id}",
                Map.of("id", "${payment.id}"), null, 200, List.of());

        // step1 returns 400 instead of expected 201 → failure
        when(httpExecutor.execute(eq("POST"), any(), any(), any()))
                .thenReturn(httpResponse(400, Map.of("error", "bad request")));

        PlanExecutionResult result = pipeline.executePlan(
                plan("plan-3", List.of(step1, step2)), "http://localhost:8080");

        assertFalse(result.isPassed());
        assertEquals(2, result.getSteps().size());
        assertFalse(result.getSteps().get(0).isPassed());
        assertFalse(result.getSteps().get(1).isPassed());
        assertNotNull(result.getSteps().get(1).getSkipReason());

        // step2 should never have been called
        verify(httpExecutor, times(1)).execute(any(), any(), any(), any());
    }

    @Test
    void executePlan_assertionMismatch_stepFails() {
        Assertion wrongAssertion = new Assertion("$.body.code", "EQUALS", "FORBIDDEN_OWNERSHIP");
        ScenarioStep step = step(0, "step-1", "POST", "/api/payments/{id}/refund",
                Map.of("id", "pay-001"), null, 403,
                List.of(wrongAssertion));

        // HTTP returns 403 (status matches) but body has different code
        when(httpExecutor.execute(eq("POST"), any(), any(), any()))
                .thenReturn(httpResponse(403, Map.of("code", "UNAUTHORIZED")));

        PlanExecutionResult result = pipeline.executePlan(
                plan("plan-4", List.of(step)), "http://localhost:8080");

        assertFalse(result.isPassed());
        StepResult sr = result.getSteps().get(0);
        assertFalse(sr.isPassed());
        assertTrue(sr.isStatusMatch());
        assertEquals(1, sr.getAssertionResults().size());
        assertFalse(sr.getAssertionResults().get(0).isPassed());
        assertEquals("UNAUTHORIZED", sr.getAssertionResults().get(0).getActualValue());
    }
}
