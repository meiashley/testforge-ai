package com.testforge.runner.pipeline;

import com.testforge.ai.scenario.Assertion;
import com.testforge.ai.scenario.ExecutionPlan;
import com.testforge.ai.scenario.FlowStep;
import com.testforge.ai.scenario.ResolvedFlow;
import com.testforge.ai.scenario.ScenarioStep;
import com.testforge.runner.assertion.AssertionEvaluator;
import com.testforge.runner.http.HttpExecutor;
import com.testforge.runner.model.HttpResponse;
import com.testforge.runner.report.ReportBuilder;
import com.testforge.runner.report.ReportWriter;
import com.testforge.runner.validation.ExecutionPlanValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionPipelinePlanValidationTest {

    @Test
    void invalidExecutionPlanDoesNotCallExecutor() {
        TrackingHttpExecutor httpExecutor = new TrackingHttpExecutor();
        ExecutionPipeline pipeline = pipeline(httpExecutor);
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setMethod("TRACE");

        ExecutionPlanValidationException ex = assertThrows(ExecutionPlanValidationException.class,
                () -> pipeline.executePlan(flow(), plan, "http://localhost:8080", Map.of("user.token", "token")));

        assertFalse(ex.getResult().isValid());
        assertEquals(0, httpExecutor.calls);
    }

    @Test
    void deprecatedExecutionPlanEntryFailsClosedAndDoesNotCallExecutor() {
        TrackingHttpExecutor httpExecutor = new TrackingHttpExecutor();
        ExecutionPipeline pipeline = pipeline(httpExecutor);

        ExecutionPlanValidationException ex = assertThrows(ExecutionPlanValidationException.class,
                () -> pipeline.executePlan(validPlan(), "http://localhost:8080"));

        assertEquals(0, httpExecutor.calls);
        assertEquals("RESOLVED_FLOW_REQUIRED", ex.getResult().errors().get(0).getCode());
        assertEquals("resolvedFlow", ex.getResult().errors().get(0).getFieldPath());
        assertTrue(ex.getMessage().contains("ExecutionPlan execution requires its source ResolvedFlow"));
    }

    @Test
    void validExecutionPlanReachesExecutor() {
        TrackingHttpExecutor httpExecutor = new TrackingHttpExecutor();
        ExecutionPipeline pipeline = pipeline(httpExecutor);

        var result = pipeline.executePlan(flow(), validPlan(), "http://localhost:8080",
                Map.of("user.token", "token"));

        assertTrue(result.isPassed());
        assertEquals(2, httpExecutor.calls);
    }

    private ExecutionPipeline pipeline(HttpExecutor httpExecutor) {
        return new ExecutionPipeline(
                httpExecutor,
                new AssertionEvaluator(),
                new ReportBuilder(),
                new ReportWriter("plan-validation-test")
        );
    }

    private static class TrackingHttpExecutor extends HttpExecutor {
        private int calls;

        @Override
        public HttpResponse execute(String method, String url, Map<String, String> headers, String body) {
            calls++;
            if ("POST".equals(method)) {
                return new HttpResponse(201, Map.of("id", "pay-1", "status", "COMPLETED"), "", Map.of(), 1);
            }
            return new HttpResponse(200, Map.of("id", "pay-1"), "", Map.of(), 1);
        }
    }

    private ResolvedFlow flow() {
        return ResolvedFlow.builder()
                .flowId("flow-payment")
                .featureId("feat-payment")
                .description("Create then fetch payment")
                .steps(List.of(
                        FlowStep.builder()
                                .order(0).stepId("step-create").role("creator").method("POST")
                                .pathTemplate("/api/payments")
                                .pathBindings(Map.of())
                                .headerBindings(Map.of("Authorization", "Bearer ${user.token}"))
                                .bodyBinding(null)
                                .outputCapture(Map.of("payment.id", "$.body.id"))
                                .build(),
                        FlowStep.builder()
                                .order(1).stepId("step-get").role("verifier").method("GET")
                                .pathTemplate("/api/payments/{id}")
                                .pathBindings(Map.of("id", "${payment.id}"))
                                .headerBindings(Map.of())
                                .bodyBinding(null)
                                .outputCapture(Map.of())
                                .build()
                ))
                .build();
    }

    private ExecutionPlan validPlan() {
        ScenarioStep create = ScenarioStep.builder()
                .order(0).stepId("step-create").role("creator").method("POST")
                .pathTemplate("/api/payments")
                .pathBindings(Map.of())
                .headerBindings(Map.of("Authorization", "Bearer ${user.token}"))
                .bodyBinding(null)
                .requestBody("{\"amount\":100}")
                .outputCapture(Map.of("payment.id", "$.body.id"))
                .expectedStatusCode(201)
                .assertions(List.of(new Assertion("$.body.status", "EQUALS", "COMPLETED")))
                .stepDescription("Create payment")
                .build();
        ScenarioStep get = ScenarioStep.builder()
                .order(1).stepId("step-get").role("verifier").method("GET")
                .pathTemplate("/api/payments/{id}")
                .pathBindings(Map.of("id", "${payment.id}"))
                .headerBindings(Map.of())
                .bodyBinding(null)
                .requestBody(null)
                .outputCapture(Map.of())
                .expectedStatusCode(200)
                .assertions(List.of(new Assertion("$.body.id", "EQUALS", "${payment.id}")))
                .stepDescription("Fetch payment")
                .build();
        return ExecutionPlan.builder()
                .planId("plan-payment")
                .source("scenario")
                .scenarioId("sc-payment")
                .scenarioName("Payment flow")
                .steps(List.of(create, get))
                .build();
    }
}
