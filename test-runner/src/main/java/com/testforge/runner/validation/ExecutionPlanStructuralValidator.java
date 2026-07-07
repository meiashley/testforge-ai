package com.testforge.runner.validation;

import com.testforge.ai.scenario.Assertion;
import com.testforge.ai.scenario.ExecutionPlan;
import com.testforge.ai.scenario.FlowStep;
import com.testforge.ai.scenario.ResolvedFlow;
import com.testforge.ai.scenario.ScenarioStep;
import com.testforge.ai.http.SupportedHttpMethods;
import com.testforge.ai.validation.ValidationFieldPath;
import com.testforge.ai.validation.ValidationIssue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecutionPlanStructuralValidator {

    private static final Set<String> SUPPORTED_ASSERTIONS =
            Set.of("EQUALS", "NOT_EQUALS", "EXISTS", "NOT_EXISTS", "CONTAINS");
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern PATH_TEMPLATE_VAR_PATTERN = Pattern.compile("\\{([^}/]+)}");

    public ExecutionPlanValidationResult validate(ExecutionPlan plan) {
        return validate(null, plan, Map.of());
    }

    public ExecutionPlanValidationResult validate(ResolvedFlow flow, ExecutionPlan plan,
                                                  Map<String, ?> initialInputs) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (plan == null) {
            issues.add(ValidationIssue.error("EXECUTION_PLAN_REQUIRED", "executionPlan",
                    "ExecutionPlan must be present"));
            return new ExecutionPlanValidationResult(issues);
        }

        validatePlanFields(plan, issues);
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return new ExecutionPlanValidationResult(issues);
        }

        validateStepUniquenessAndOrder(plan, issues);
        if (flow != null) {
            validateImmutableFlowFields(flow, plan, issues);
        }
        validateMetadata(plan, issues);
        validateSteps(plan, initialInputs != null ? initialInputs : Map.of(), issues);
        return new ExecutionPlanValidationResult(issues);
    }

    private void validatePlanFields(ExecutionPlan plan, List<ValidationIssue> issues) {
        requireNonBlank(plan.getPlanId(), "executionPlan.planId", "EXECUTION_PLAN_ID_REQUIRED",
                "ExecutionPlan planId must be present and non-blank", issues);
        requireNonBlank(plan.getSource(), "executionPlan.source", "EXECUTION_PLAN_SOURCE_REQUIRED",
                "ExecutionPlan source must be present and non-blank", issues);
        requireNonBlank(plan.getScenarioId(), "executionPlan.scenarioId", "EXECUTION_PLAN_SCENARIO_ID_REQUIRED",
                "ExecutionPlan scenarioId must be present and non-blank", issues);
        requireNonBlank(plan.getScenarioName(), "executionPlan.scenarioName", "EXECUTION_PLAN_SCENARIO_NAME_REQUIRED",
                "ExecutionPlan scenarioName must be present and non-blank", issues);
        if (plan.getSteps() == null) {
            issues.add(ValidationIssue.error("EXECUTION_PLAN_STEPS_REQUIRED", "executionPlan.steps",
                    "ExecutionPlan steps must be present"));
        } else if (plan.getSteps().isEmpty()) {
            issues.add(ValidationIssue.error("EXECUTION_PLAN_STEPS_EMPTY", "executionPlan.steps",
                    "ExecutionPlan steps must contain at least one step"));
        }
    }

    private void validateStepUniquenessAndOrder(ExecutionPlan plan, List<ValidationIssue> issues) {
        Map<String, Integer> stepIds = new HashMap<>();
        Map<Integer, Integer> orders = new HashMap<>();
        Integer previousOrder = null;

        for (int i = 0; i < plan.getSteps().size(); i++) {
            ScenarioStep step = plan.getSteps().get(i);
            if (step == null) {
                issues.add(ValidationIssue.error("EXECUTION_STEP_REQUIRED", stepPath(i),
                        "ExecutionPlan step must be present"));
                continue;
            }

            if (!isBlank(step.getStepId())) {
                Integer first = stepIds.putIfAbsent(step.getStepId(), i);
                if (first != null) {
                    issues.add(ValidationIssue.error("DUPLICATE_EXECUTION_STEP_ID", stepPath(i, "stepId"),
                            "Duplicate step id '" + step.getStepId() + "' also appears at executionPlan.steps[" + first + "].stepId"));
                }
            }

            Integer firstOrder = orders.putIfAbsent(step.getOrder(), i);
            if (firstOrder != null) {
                issues.add(ValidationIssue.error("DUPLICATE_EXECUTION_STEP_ORDER", stepPath(i, "order"),
                        "Duplicate step order " + step.getOrder() + " also appears at executionPlan.steps[" + firstOrder + "].order"));
            }

            if (previousOrder != null && step.getOrder() <= previousOrder) {
                issues.add(ValidationIssue.error("AMBIGUOUS_EXECUTION_STEP_ORDER", stepPath(i, "order"),
                        "Step list execution order must be strictly increasing by order"));
            }
            previousOrder = step.getOrder();
        }
    }

    private void validateImmutableFlowFields(ResolvedFlow flow, ExecutionPlan plan, List<ValidationIssue> issues) {
        if (flow.getSteps() == null) {
            issues.add(ValidationIssue.error("RESOLVED_FLOW_STEPS_REQUIRED", "resolvedFlow.steps",
                    "Resolved flow steps must be present for plan validation"));
            return;
        }

        List<FlowStep> flowSteps = flow.getSteps();
        List<ScenarioStep> planSteps = plan.getSteps();
        if (flowSteps.size() != planSteps.size()) {
            issues.add(ValidationIssue.error("EXECUTION_PLAN_STEP_COUNT_MISMATCH", "executionPlan.steps",
                    "ExecutionPlan step count must match resolved flow step count"));
        }

        Map<String, FlowStep> flowStepsById = new HashMap<>();
        for (int flowIndex = 0; flowIndex < flowSteps.size(); flowIndex++) {
            FlowStep flowStep = flowSteps.get(flowIndex);
            if (flowStep != null && !isBlank(flowStep.getStepId())) {
                flowStepsById.put(flowStep.getStepId(), flowStep);
            }
        }

        Map<String, Integer> planStepCounts = new HashMap<>();
        for (ScenarioStep step : planSteps) {
            if (step != null && !isBlank(step.getStepId())) {
                planStepCounts.merge(step.getStepId(), 1, Integer::sum);
            }
        }

        for (int flowIndex = 0; flowIndex < flowSteps.size(); flowIndex++) {
            FlowStep flowStep = flowSteps.get(flowIndex);
            if (flowStep != null && !isBlank(flowStep.getStepId())
                    && planStepCounts.getOrDefault(flowStep.getStepId(), 0) == 0) {
                issues.add(ValidationIssue.error("RESOLVED_FLOW_STEP_MISSING",
                        "resolvedFlow.steps[" + flowIndex + "].stepId",
                        "Resolved flow step '" + flowStep.getStepId() + "' is missing from ExecutionPlan"));
            }
        }

        for (int i = 0; i < planSteps.size(); i++) {
            ScenarioStep step = planSteps.get(i);
            if (step != null && !isBlank(step.getStepId()) && !flowStepsById.containsKey(step.getStepId())) {
                issues.add(ValidationIssue.error("UNEXPECTED_EXECUTION_STEP", stepPath(i, "stepId"),
                        "ExecutionPlan step id '" + step.getStepId() + "' is not present in the resolved flow"));
            }
        }

        int commonSize = Math.min(flowSteps.size(), planSteps.size());
        for (int i = 0; i < commonSize; i++) {
            FlowStep flowStep = flowSteps.get(i);
            ScenarioStep planStep = planSteps.get(i);
            if (flowStep == null || planStep == null) {
                continue;
            }
            if (!Objects.equals(flowStep.getStepId(), planStep.getStepId())) {
                issues.add(ValidationIssue.error("IMMUTABLE_STEP_ID_MODIFIED", stepPath(i, "stepId"),
                        "ExecutionPlan modified immutable resolved flow step identity; expected '"
                                + flowStep.getStepId() + "' but was '" + planStep.getStepId() + "'"));
                issues.add(ValidationIssue.error("EXECUTION_STEP_SEQUENCE_MODIFIED", stepPath(i, "stepId"),
                        "ExecutionPlan list order must match resolved flow step order because executor uses list order"));
            }
        }

        for (int i = 0; i < plan.getSteps().size(); i++) {
            ScenarioStep step = plan.getSteps().get(i);
            if (step == null || isBlank(step.getStepId())) {
                continue;
            }
            FlowStep flowStep = flowStepsById.get(step.getStepId());
            if (flowStep == null) {
                continue;
            }

            compareImmutable(flowStep.getStepId(), step.getStepId(), stepPath(i, "stepId"), "IMMUTABLE_STEP_ID_MODIFIED", issues);
            compareImmutable(flowStep.getOrder(), step.getOrder(), stepPath(i, "order"), "IMMUTABLE_STEP_ORDER_MODIFIED", issues);
            compareImmutable(flowStep.getRole(), step.getRole(), stepPath(i, "role"), "IMMUTABLE_STEP_ROLE_MODIFIED", issues);
            compareImmutable(normalizedMethod(flowStep.getMethod()), normalizedMethod(step.getMethod()),
                    stepPath(i, "method"), "IMMUTABLE_STEP_METHOD_MODIFIED", issues);
            compareImmutable(flowStep.getPathTemplate(), step.getPathTemplate(),
                    stepPath(i, "pathTemplate"), "IMMUTABLE_STEP_PATH_TEMPLATE_MODIFIED", issues);
            compareImmutable(nullToEmpty(flowStep.getPathBindings()), nullToEmpty(step.getPathBindings()),
                    stepPath(i, "pathBindings"), "IMMUTABLE_STEP_PATH_BINDINGS_MODIFIED", issues);
            compareImmutable(nullToEmpty(flowStep.getHeaderBindings()), nullToEmpty(step.getHeaderBindings()),
                    stepPath(i, "headerBindings"), "IMMUTABLE_STEP_HEADER_BINDINGS_MODIFIED", issues);
            compareImmutable(nullToEmpty(flowStep.getOutputCapture()), nullToEmpty(step.getOutputCapture()),
                    stepPath(i, "outputCapture"), "IMMUTABLE_STEP_OUTPUT_CAPTURE_MODIFIED", issues);
        }
    }

    private void validateMetadata(ExecutionPlan plan, List<ValidationIssue> issues) {
        if (plan.getMetadata() == null) {
            return;
        }
        Object testData = plan.getMetadata().get("testData");
        if (testData == null) {
            return;
        }
        if (!(testData instanceof Map<?, ?> data)) {
            issues.add(ValidationIssue.error("EXECUTION_PLAN_TEST_DATA_INVALID", "executionPlan.metadata.testData",
                    "metadata.testData must be an object when present"));
            return;
        }
        int i = 0;
        for (Map.Entry<?, ?> entry : data.entrySet()) {
            String path = "executionPlan.metadata.testData[" + i + "]";
            if (isBlank(String.valueOf(entry.getKey()))) {
                issues.add(ValidationIssue.error("EXECUTION_PLAN_TEST_DATA_KEY_REQUIRED", path,
                        "metadata.testData key must be present and non-blank"));
            }
            if (entry.getValue() == null) {
                issues.add(ValidationIssue.error("EXECUTION_PLAN_TEST_DATA_VALUE_REQUIRED", path,
                        "metadata.testData value must not be null"));
            }
            i++;
        }
    }

    private void validateSteps(ExecutionPlan plan, Map<String, ?> initialInputs, List<ValidationIssue> issues) {
        Set<String> availableVariables = new HashSet<>();
        availableVariables.addAll(nonNullKeys(initialInputs));
        availableVariables.addAll(testDataKeys(plan));

        for (int i = 0; i < plan.getSteps().size(); i++) {
            ScenarioStep step = plan.getSteps().get(i);
            if (step == null) {
                continue;
            }

            validateRequiredStepFields(step, i, issues);
            validateBindings(step, i, availableVariables, issues);
            validateAssertions(step, i, availableVariables, issues);
            validateOutputCapture(step, i, issues);

            if (step.getOutputCapture() != null) {
                availableVariables.addAll(step.getOutputCapture().keySet().stream()
                        .filter(key -> !isBlank(key))
                        .toList());
            }
        }
    }

    private void validateRequiredStepFields(ScenarioStep step, int index, List<ValidationIssue> issues) {
        requireNonBlank(step.getStepId(), stepPath(index, "stepId"), "EXECUTION_STEP_ID_REQUIRED",
                "ExecutionPlan stepId must be present and non-blank", issues);
        requireNonBlank(step.getRole(), stepPath(index, "role"), "EXECUTION_STEP_ROLE_REQUIRED",
                "ExecutionPlan step role must be present and non-blank", issues);
        if (isBlank(step.getMethod())) {
            issues.add(ValidationIssue.error("EXECUTION_STEP_METHOD_REQUIRED", stepPath(index, "method"),
                    "ExecutionPlan step method must be present and non-blank"));
        } else if (!SupportedHttpMethods.isSupported(step.getMethod())) {
            issues.add(ValidationIssue.error("UNSUPPORTED_EXECUTION_STEP_METHOD", stepPath(index, "method"),
                    "ExecutionPlan step method '" + step.getMethod() + "' is not supported"));
        }
        requireNonBlank(step.getPathTemplate(), stepPath(index, "pathTemplate"), "EXECUTION_STEP_PATH_TEMPLATE_REQUIRED",
                "ExecutionPlan step pathTemplate must be present and non-blank", issues);
        if (step.getExpectedStatusCode() < 100 || step.getExpectedStatusCode() > 599) {
            issues.add(ValidationIssue.error("EXECUTION_STEP_EXPECTED_STATUS_INVALID",
                    stepPath(index, "expectedStatusCode"),
                    "ExecutionPlan step expectedStatusCode must be between 100 and 599"));
        }
        if (step.getAssertions() == null) {
            issues.add(ValidationIssue.error("EXECUTION_STEP_ASSERTIONS_REQUIRED", stepPath(index, "assertions"),
                    "ExecutionPlan step assertions must be present"));
        }
    }

    private void validateBindings(ScenarioStep step, int index, Set<String> availableVariables,
                                  List<ValidationIssue> issues) {
        Set<String> templateVariables = pathTemplateVariables(step.getPathTemplate());
        Map<String, String> pathBindings = step.getPathBindings() != null ? step.getPathBindings() : Map.of();

        for (String templateVariable : templateVariables) {
            if (!pathBindings.containsKey(templateVariable)) {
                issues.add(ValidationIssue.error("PATH_BINDING_MISSING",
                        ValidationFieldPath.mapKey(stepPath(index, "pathBindings"), templateVariable),
                        "Path template variable '{" + templateVariable + "}' must have a path binding"));
            }
        }

        for (Map.Entry<String, String> binding : pathBindings.entrySet()) {
            String fieldPath = ValidationFieldPath.mapKey(stepPath(index, "pathBindings"), binding.getKey());
            if (isBlank(binding.getKey())) {
                issues.add(ValidationIssue.error("PATH_BINDING_TARGET_REQUIRED", fieldPath,
                        "Path binding target must be present and non-blank"));
            } else if (!templateVariables.contains(binding.getKey())) {
                issues.add(ValidationIssue.error("PATH_BINDING_TARGET_INVALID", fieldPath,
                        "Path binding target '" + binding.getKey() + "' is not present in pathTemplate"));
            }
            if (isBlank(binding.getValue())) {
                issues.add(ValidationIssue.error("PATH_BINDING_VALUE_REQUIRED", fieldPath,
                        "Path binding value must be present and non-blank"));
                continue;
            }
            validateTemplateVariables(binding.getValue(), fieldPath, availableVariables, issues);
        }

        if (step.getHeaderBindings() != null) {
            for (Map.Entry<String, String> binding : step.getHeaderBindings().entrySet()) {
                String fieldPath = ValidationFieldPath.mapKey(stepPath(index, "headerBindings"), binding.getKey());
                if (isBlank(binding.getKey())) {
                    issues.add(ValidationIssue.error("HEADER_BINDING_TARGET_REQUIRED", fieldPath,
                            "Header binding target must be present and non-blank"));
                }
                if (binding.getValue() == null) {
                    issues.add(ValidationIssue.error("HEADER_BINDING_VALUE_REQUIRED", fieldPath,
                            "Header binding value must be present"));
                    continue;
                }
                validateTemplateVariables(binding.getValue(), fieldPath, availableVariables, issues);
            }
        }

        validateTemplateVariables(step.getRequestBody(), stepPath(index, "requestBody"), availableVariables, issues);
    }

    private void validateAssertions(ScenarioStep step, int stepIndex, Set<String> availableVariables,
                                    List<ValidationIssue> issues) {
        if (step.getAssertions() == null) {
            return;
        }
        for (int i = 0; i < step.getAssertions().size(); i++) {
            Assertion assertion = step.getAssertions().get(i);
            String base = stepPath(stepIndex, "assertions[" + i + "]");
            if (assertion == null) {
                issues.add(ValidationIssue.error("EXECUTION_ASSERTION_REQUIRED", base,
                        "ExecutionPlan assertion must be present"));
                continue;
            }
            requireNonBlank(assertion.getPath(), base + ".path", "EXECUTION_ASSERTION_PATH_REQUIRED",
                    "ExecutionPlan assertion path must be present and non-blank", issues);
            if (!isBlank(assertion.getPath()) && !isSupportedJsonPath(assertion.getPath())) {
                issues.add(ValidationIssue.error("EXECUTION_ASSERTION_PATH_UNSUPPORTED", base + ".path",
                        "ExecutionPlan assertion path must be $.body, $.body.<field>, or $.headers.<header>"));
            }
            if (isBlank(assertion.getType())) {
                issues.add(ValidationIssue.error("EXECUTION_ASSERTION_TYPE_REQUIRED", base + ".type",
                        "ExecutionPlan assertion type must be present and non-blank"));
                continue;
            }
            String type = assertion.getType().trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_ASSERTIONS.contains(type)) {
                issues.add(ValidationIssue.error("UNSUPPORTED_EXECUTION_ASSERTION_TYPE", base + ".type",
                        "ExecutionPlan assertion type '" + assertion.getType() + "' is not supported by the executor"));
                continue;
            }
            if (requiresExpected(type) && assertion.getExpected() == null) {
                issues.add(ValidationIssue.error("EXECUTION_ASSERTION_EXPECTED_REQUIRED", base + ".expected",
                        "ExecutionPlan assertion expected value is required for " + type));
            }
            if (("EXISTS".equals(type) || "NOT_EXISTS".equals(type)) && assertion.getExpected() != null) {
                issues.add(ValidationIssue.error("EXECUTION_ASSERTION_EXPECTED_MUST_BE_NULL", base + ".expected",
                        "ExecutionPlan assertion expected value must be null for " + type));
            }
            validateTemplateVariables(assertion.getPath(), base + ".path", availableVariables, issues);
            if (assertion.getExpected() instanceof String expected) {
                validateTemplateVariables(expected, base + ".expected", availableVariables, issues);
            }
        }
    }

    private void validateOutputCapture(ScenarioStep step, int index, List<ValidationIssue> issues) {
        if (step.getOutputCapture() == null) {
            return;
        }
        for (Map.Entry<String, String> capture : step.getOutputCapture().entrySet()) {
            String fieldPath = ValidationFieldPath.mapKey(stepPath(index, "outputCapture"), capture.getKey());
            if (isBlank(capture.getKey())) {
                issues.add(ValidationIssue.error("OUTPUT_CAPTURE_NAME_REQUIRED", fieldPath,
                        "Output capture name must be present and non-blank"));
            }
            if (isBlank(capture.getValue())) {
                issues.add(ValidationIssue.error("OUTPUT_CAPTURE_SOURCE_REQUIRED", fieldPath,
                        "Output capture source must be present and non-blank"));
            } else if (!isSupportedJsonPath(capture.getValue())) {
                issues.add(ValidationIssue.error("OUTPUT_CAPTURE_SOURCE_UNSUPPORTED", fieldPath,
                        "Output capture source must be $.body, $.body.<field>, or $.headers.<header>"));
            }
        }
    }

    private void validateTemplateVariables(String template, String fieldPath, Set<String> availableVariables,
                                           List<ValidationIssue> issues) {
        if (template == null) {
            return;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!availableVariables.contains(variable)) {
                issues.add(ValidationIssue.error("UNDEFINED_EXECUTION_VARIABLE", fieldPath,
                        "Variable '${" + variable + "}' is not provided by initial inputs, metadata.testData, or previous output captures"));
            }
        }
    }

    private boolean requiresExpected(String type) {
        return "EQUALS".equals(type) || "NOT_EQUALS".equals(type) || "CONTAINS".equals(type);
    }

    private Set<String> pathTemplateVariables(String pathTemplate) {
        if (pathTemplate == null) {
            return Set.of();
        }
        Set<String> variables = new HashSet<>();
        Matcher matcher = PATH_TEMPLATE_VAR_PATTERN.matcher(pathTemplate);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private Set<String> testDataKeys(ExecutionPlan plan) {
        if (plan.getMetadata() == null || !(plan.getMetadata().get("testData") instanceof Map<?, ?> testData)) {
            return Set.of();
        }
        Set<String> keys = new HashSet<>();
        for (Map.Entry<?, ?> entry : testData.entrySet()) {
            if (entry.getValue() != null && !isBlank(String.valueOf(entry.getKey()))) {
                keys.add(String.valueOf(entry.getKey()));
            }
        }
        return keys;
    }

    private Set<String> nonNullKeys(Map<String, ?> map) {
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            if (entry.getValue() != null && !isBlank(entry.getKey())) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    private boolean isSupportedJsonPath(String path) {
        return "$.body".equals(path)
                || path.startsWith("$.body.")
                || path.startsWith("$.headers.");
    }

    private String normalizedMethod(String method) {
        return SupportedHttpMethods.normalize(method);
    }

    private Map<String, String> nullToEmpty(Map<String, String> map) {
        return map == null ? Map.of() : map;
    }

    private void compareImmutable(Object expected, Object actual, String fieldPath, String code,
                                  List<ValidationIssue> issues) {
        if (!Objects.equals(expected, actual)) {
            issues.add(ValidationIssue.error(code, fieldPath,
                    "ExecutionPlan modified immutable resolved flow field; expected '" + expected + "' but was '" + actual + "'"));
        }
    }

    private void requireNonBlank(String value, String fieldPath, String code, String message,
                                 List<ValidationIssue> issues) {
        if (isBlank(value)) {
            issues.add(ValidationIssue.error(code, fieldPath, message));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stepPath(int index) {
        return "executionPlan.steps[" + index + "]";
    }

    private String stepPath(int index, String field) {
        return stepPath(index) + "." + field;
    }
}
