package com.testforge.runner.validation;

import com.testforge.ai.scenario.Assertion;
import com.testforge.ai.scenario.ExecutionPlan;
import com.testforge.ai.scenario.FlowStep;
import com.testforge.ai.scenario.ResolvedFlow;
import com.testforge.ai.scenario.ScenarioStep;
import com.testforge.ai.validation.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionPlanStructuralValidatorTest {

    private final ExecutionPlanStructuralValidator validator = new ExecutionPlanStructuralValidator();

    @Test
    void validPlanWithInitialInputAndPreviousCapture_passes() {
        ExecutionPlanValidationResult result = validator.validate(flow(), validPlan(),
                Map.of("user.token", "token-123"));

        assertTrue(result.isValid(), codes(result).toString());
    }

    @Test
    void validationResultDefensivelyCopiesIssuesAndExposesNoSetters() {
        List<ValidationIssue> issues = new ArrayList<>(List.of(
                ValidationIssue.error("EXECUTION_PLAN_REQUIRED", "executionPlan", "required")));

        ExecutionPlanValidationResult result = new ExecutionPlanValidationResult(issues);
        issues.clear();

        assertEquals(1, result.getIssues().size());
        assertThrows(UnsupportedOperationException.class, () -> result.getIssues().clear());
        assertTrue(Arrays.stream(ExecutionPlanValidationResult.class.getMethods())
                        .noneMatch(method -> method.getName().startsWith("set")),
                "ExecutionPlanValidationResult must not expose setter methods");
    }

    @Test
    void requiredPlanFieldsMissing_areErrors() {
        ExecutionPlan plan = validPlan();
        plan.setPlanId(" ");
        plan.setSource(null);
        plan.setScenarioId("");
        plan.setScenarioName(null);
        plan.setSteps(null);

        ExecutionPlanValidationResult result = validator.validate(flow(), plan, Map.of());

        assertFalse(result.isValid());
        assertCodes(result,
                "EXECUTION_PLAN_ID_REQUIRED",
                "EXECUTION_PLAN_SOURCE_REQUIRED",
                "EXECUTION_PLAN_SCENARIO_ID_REQUIRED",
                "EXECUTION_PLAN_SCENARIO_NAME_REQUIRED",
                "EXECUTION_PLAN_STEPS_REQUIRED");
    }

    @Test
    void requiredStepFieldsMissing_areErrors() {
        ExecutionPlan plan = validPlan();
        ScenarioStep step = plan.getSteps().get(0);
        step.setStepId(" ");
        step.setRole(null);
        step.setMethod("");
        step.setPathTemplate(" ");
        step.setExpectedStatusCode(99);
        step.setAssertions(null);

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result,
                "EXECUTION_STEP_ID_REQUIRED",
                "EXECUTION_STEP_ROLE_REQUIRED",
                "EXECUTION_STEP_METHOD_REQUIRED",
                "EXECUTION_STEP_PATH_TEMPLATE_REQUIRED",
                "EXECUTION_STEP_EXPECTED_STATUS_INVALID",
                "EXECUTION_STEP_ASSERTIONS_REQUIRED");
    }

    @Test
    void duplicateStepIdAndOrder_areErrors() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(1).setStepId("step-create");
        plan.getSteps().get(1).setOrder(0);

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "DUPLICATE_EXECUTION_STEP_ID", "DUPLICATE_EXECUTION_STEP_ORDER",
                "AMBIGUOUS_EXECUTION_STEP_ORDER");
    }

    @Test
    void immutableFlowFieldModification_isError() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setMethod("GET");

        ExecutionPlanValidationResult result = validator.validate(flow(), plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "IMMUTABLE_STEP_METHOD_MODIFIED");
    }

    @Test
    void bodyBindingModification_isNotImmutableError() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setBodyBinding(Map.of("currently", "not validated"));

        ExecutionPlanValidationResult result = validator.validate(flow(), plan, Map.of("user.token", "token"));

        assertTrue(result.getIssues().stream()
                .noneMatch(issue -> "IMMUTABLE_STEP_BODY_BINDING_MODIFIED".equals(issue.getCode())));
    }

    @Test
    void missingResolvedFlowStep_isError() {
        ExecutionPlan plan = validPlan();
        plan.setSteps(List.of(plan.getSteps().get(0)));

        ExecutionPlanValidationResult result = validator.validate(flow(), plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "EXECUTION_PLAN_STEP_COUNT_MISMATCH", "RESOLVED_FLOW_STEP_MISSING");
    }

    @Test
    void extraPlanStep_isError() {
        ExecutionPlan plan = validPlan();
        ScenarioStep extra = step("step-extra", 2, "auditor", "GET", "/api/audit", Map.of(), Map.of());
        plan.setSteps(List.of(plan.getSteps().get(0), plan.getSteps().get(1), extra));

        ExecutionPlanValidationResult result = validator.validate(flow(), plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "EXECUTION_PLAN_STEP_COUNT_MISMATCH", "UNEXPECTED_EXECUTION_STEP");
    }

    @Test
    void stepIdentityModification_isError() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setStepId("step-renamed");

        ExecutionPlanValidationResult result = validator.validate(flow(), plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "RESOLVED_FLOW_STEP_MISSING", "UNEXPECTED_EXECUTION_STEP",
                "IMMUTABLE_STEP_ID_MODIFIED", "EXECUTION_STEP_SEQUENCE_MODIFIED");
    }

    @Test
    void listOrderModification_isErrorEvenWhenOrdersAreUnchanged() {
        ExecutionPlan plan = validPlan();
        plan.setSteps(List.of(plan.getSteps().get(1), plan.getSteps().get(0)));

        ExecutionPlanValidationResult result = validator.validate(flow(), plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "AMBIGUOUS_EXECUTION_STEP_ORDER",
                "IMMUTABLE_STEP_ID_MODIFIED", "EXECUTION_STEP_SEQUENCE_MODIFIED");
    }

    @Test
    void undefinedVariable_isError() {
        ExecutionPlan plan = validPlan();
        plan.setMetadata(Map.of());

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of());

        assertFalse(result.isValid());
        assertCodes(result, "UNDEFINED_EXECUTION_VARIABLE");
    }

    @Test
    void forwardReference_isError() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setPathTemplate("/api/payments/{id}");
        plan.getSteps().get(0).setPathBindings(Map.of("id", "${payment.id}"));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "UNDEFINED_EXECUTION_VARIABLE");
        assertEquals("executionPlan.steps[0].pathBindings['id']", result.errors().stream()
                .filter(issue -> "UNDEFINED_EXECUTION_VARIABLE".equals(issue.getCode()))
                .findFirst().orElseThrow().getFieldPath());
    }

    @Test
    void invalidPathBinding_isError() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(1).setPathBindings(Map.of("wrong", "${payment.id}"));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "PATH_BINDING_MISSING", "PATH_BINDING_TARGET_INVALID");
    }

    @Test
    void nullPathBindingValue_isRejected() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setPathTemplate("/api/payments/{paymentId}");
        Map<String, String> bindings = new HashMap<>();
        bindings.put("paymentId", null);
        plan.getSteps().get(0).setPathBindings(bindings);

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "PATH_BINDING_VALUE_REQUIRED");
        assertEquals("executionPlan.steps[0].pathBindings['paymentId']", result.errors().stream()
                .filter(issue -> "PATH_BINDING_VALUE_REQUIRED".equals(issue.getCode()))
                .findFirst().orElseThrow().getFieldPath());
        assertFalse(codes(result).contains("UNDEFINED_EXECUTION_VARIABLE"));
    }

    @Test
    void blankPathBindingValue_isRejected() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setPathTemplate("/api/payments/{paymentId}");
        plan.getSteps().get(0).setPathBindings(Map.of("paymentId", "   "));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "PATH_BINDING_VALUE_REQUIRED");
        assertEquals("executionPlan.steps[0].pathBindings['paymentId']", result.errors().stream()
                .filter(issue -> "PATH_BINDING_VALUE_REQUIRED".equals(issue.getCode()))
                .findFirst().orElseThrow().getFieldPath());
    }

    @Test
    void nullHeaderBindingValue_isRejected() {
        ExecutionPlan plan = validPlan();
        Map<String, String> bindings = new HashMap<>();
        bindings.put("Authorization", null);
        plan.getSteps().get(0).setHeaderBindings(bindings);

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "HEADER_BINDING_VALUE_REQUIRED");
        assertEquals("executionPlan.steps[0].headerBindings['Authorization']", result.errors().stream()
                .filter(issue -> "HEADER_BINDING_VALUE_REQUIRED".equals(issue.getCode()))
                .findFirst().orElseThrow().getFieldPath());
        assertFalse(codes(result).contains("UNDEFINED_EXECUTION_VARIABLE"));
    }

    @Test
    void emptyHeaderBindingValue_behaviour_matchesExistingContract() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setHeaderBindings(Map.of("Authorization", ""));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertTrue(result.isValid(), codes(result).toString());
    }

    @Test
    void validBindingValues_stillPass() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(1).setHeaderBindings(Map.of(
                "X-Literal", "literal-value",
                "X-Initial", "Bearer ${user.token}"
        ));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertTrue(result.isValid(), codes(result).toString());
    }

    @Test
    void invalidOutputCapture_isError() {
        ExecutionPlan plan = validPlan();
        Map<String, String> capture = new HashMap<>();
        capture.put("", "$.status");
        plan.getSteps().get(0).setOutputCapture(capture);

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "OUTPUT_CAPTURE_NAME_REQUIRED", "OUTPUT_CAPTURE_SOURCE_UNSUPPORTED");
    }

    @Test
    void statusCodeOutputCapture_isSupported() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setOutputCapture(Map.of(
                "payment.id", "$.body.id",
                "payment.statusCode", "$.statusCode"));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertTrue(result.isValid(), codes(result).toString());
        assertFalse(codes(result).contains("OUTPUT_CAPTURE_SOURCE_UNSUPPORTED"));
    }

    @Test
    void unsupportedStatusCodeAliases_areRejected() {
        Map<String, String> unsupportedSources = Map.of(
                "statusCode.value", "$.statusCode.value",
                "status", "$.status",
                "httpStatus", "$.httpStatus",
                "response.status", "$.response.status"
        );

        for (Map.Entry<String, String> unsupported : unsupportedSources.entrySet()) {
            ExecutionPlan plan = validPlan();
            plan.getSteps().get(0).setOutputCapture(Map.of(unsupported.getKey(), unsupported.getValue()));

            ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

            assertFalse(result.isValid(), unsupported.toString());
            ValidationIssue issue = result.errors().stream()
                    .filter(i -> "OUTPUT_CAPTURE_SOURCE_UNSUPPORTED".equals(i.getCode()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("executionPlan.steps[0].outputCapture['" + unsupported.getKey() + "']",
                    issue.getFieldPath());
        }
    }

    @Test
    void unsupportedAssertionAndMissingExpected_areErrors() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setAssertions(List.of(
                new Assertion("$.body.status", "MATCHES_REGEX", "COMP.*"),
                new Assertion("$.body.status", "EQUALS", null),
                new Assertion("$.body.status", "EXISTS", "ignored")
        ));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result,
                "UNSUPPORTED_EXECUTION_ASSERTION_TYPE",
                "EXECUTION_ASSERTION_EXPECTED_REQUIRED",
                "EXECUTION_ASSERTION_EXPECTED_MUST_BE_NULL");
    }

    @Test
    void matchesRegexAssertion_isRejectedAsUnsupported() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setAssertions(List.of(
                new Assertion("$.body.status", "MATCHES_REGEX", "COMP.*")
        ));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertEquals(1, result.getIssues().size());
        ValidationIssue issue = result.getIssues().get(0);
        assertEquals("UNSUPPORTED_EXECUTION_ASSERTION_TYPE", issue.getCode());
        assertEquals("executionPlan.steps[0].assertions[0].type", issue.getFieldPath());
    }

    @Test
    void unsupportedAssertionPath_isError() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setAssertions(List.of(new Assertion("$.status", "EQUALS", 201)));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        assertCodes(result, "EXECUTION_ASSERTION_PATH_UNSUPPORTED");
    }

    @Test
    void metadataTestDataProvidesVariables() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(0).setHeaderBindings(Map.of("Authorization", "Bearer ${user.token}"));
        plan.setMetadata(Map.of("testData", Map.of("user.token", "from-metadata")));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of());

        assertTrue(result.isValid(), codes(result).toString());
    }

    @Test
    void mapKeyFieldPathsAreEscaped() {
        ExecutionPlan plan = validPlan();
        plan.getSteps().get(1).setPathTemplate("/api/payments/{order.id}");
        plan.getSteps().get(1).setPathBindings(Map.of("order.id", "${missing.value}"));
        plan.getSteps().get(1).setHeaderBindings(Map.of("X Weird 'Header\\Name", "${missing.header}"));
        plan.getSteps().get(1).setOutputCapture(Map.of("capture 'one\\two", "$.status"));

        ExecutionPlanValidationResult result = validator.validate(null, plan, Map.of("user.token", "token"));

        assertFalse(result.isValid());
        List<String> paths = result.getIssues().stream()
                .map(ValidationIssue::getFieldPath)
                .toList();
        assertTrue(paths.contains("executionPlan.steps[1].pathBindings['order.id']"), paths.toString());
        assertTrue(paths.contains("executionPlan.steps[1].headerBindings['X Weird \\'Header\\\\Name']"), paths.toString());
        assertTrue(paths.contains("executionPlan.steps[1].outputCapture['capture \\'one\\\\two']"), paths.toString());
    }

    @Test
    void headMethod_isSupported() {
        ExecutionPlan plan = validPlan();
        ResolvedFlow flow = flow();
        plan.getSteps().get(1).setMethod("HEAD");
        flow.getSteps().get(1).setMethod("HEAD");

        ExecutionPlanValidationResult result = validator.validate(flow, plan, Map.of("user.token", "token"));

        assertTrue(result.getIssues().stream()
                .noneMatch(issue -> "UNSUPPORTED_EXECUTION_STEP_METHOD".equals(issue.getCode())));
    }

    private void assertCodes(ExecutionPlanValidationResult result, String... expectedCodes) {
        List<String> codes = codes(result);
        for (String expectedCode : expectedCodes) {
            assertTrue(codes.contains(expectedCode), "Expected issue code " + expectedCode + " in " + codes);
        }
    }

    private List<String> codes(ExecutionPlanValidationResult result) {
        return result.getIssues().stream()
                .map(ValidationIssue::getCode)
                .toList();
    }

    private ResolvedFlow flow() {
        return ResolvedFlow.builder()
                .flowId("flow-payment")
                .featureId("feat-payment")
                .description("Create then fetch payment")
                .steps(List.of(
                        FlowStep.builder()
                                .order(0)
                                .stepId("step-create")
                                .role("creator")
                                .method("POST")
                                .pathTemplate("/api/payments")
                                .pathBindings(Map.of())
                                .headerBindings(Map.of("Authorization", "Bearer ${user.token}"))
                                .bodyBinding(null)
                                .outputCapture(Map.of("payment.id", "$.body.id"))
                                .build(),
                        FlowStep.builder()
                                .order(1)
                                .stepId("step-get")
                                .role("verifier")
                                .method("GET")
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
                .order(0)
                .stepId("step-create")
                .role("creator")
                .method("POST")
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
                .order(1)
                .stepId("step-get")
                .role("verifier")
                .method("GET")
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

    private ScenarioStep step(String stepId, int order, String role, String method, String pathTemplate,
                              Map<String, String> pathBindings, Map<String, String> outputCapture) {
        return ScenarioStep.builder()
                .order(order)
                .stepId(stepId)
                .role(role)
                .method(method)
                .pathTemplate(pathTemplate)
                .pathBindings(pathBindings)
                .headerBindings(Map.of())
                .bodyBinding(null)
                .requestBody(null)
                .outputCapture(outputCapture)
                .expectedStatusCode(200)
                .assertions(List.of())
                .stepDescription("Extra")
                .build();
    }
}
