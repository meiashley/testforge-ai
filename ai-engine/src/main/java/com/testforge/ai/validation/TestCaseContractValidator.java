package com.testforge.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.model.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class TestCaseContractValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<ContractViolation> validate(List<TestCase> tests, EndpointSpec spec) {
        List<ContractViolation> violations = new ArrayList<>();
        Pattern pathPattern = buildPathPattern(spec.getPath());
        Set<String> validStatusCodes = spec.getResponseSchemas().keySet();
        Set<String> schemaFields = parseSchemaFields(spec.getRequestBodySchema());

        for (TestCase test : tests) {
            String id = test.getId();
            String name = test.getName();
            var req = test.getRequest();
            var exp = test.getExpected();

            if (req != null) {
                if (req.getPath() != null && !pathPattern.matcher(req.getPath()).matches()) {
                    violations.add(new ContractViolation(id, name, "PATH_NOT_FOUND",
                            "Path '" + req.getPath() + "' does not match spec path '" + spec.getPath() + "'"));
                }

                if (req.getMethod() != null && !req.getMethod().equalsIgnoreCase(spec.getMethod())) {
                    violations.add(new ContractViolation(id, name, "METHOD_MISMATCH",
                            "Method '" + req.getMethod() + "' does not match spec method '" + spec.getMethod() + "'"));
                }

                if (req.getBody() != null && !schemaFields.isEmpty()) {
                    for (String field : req.getBody().keySet()) {
                        if (!schemaFields.contains(field)) {
                            violations.add(new ContractViolation(id, name, "FIELD_NOT_IN_SCHEMA",
                                    "Request body field '" + field + "' is not defined in the spec schema"));
                        }
                    }
                }
            }

            if (exp != null && !validStatusCodes.isEmpty()) {
                String statusStr = String.valueOf(exp.getStatus());
                if (!validStatusCodes.contains(statusStr)) {
                    violations.add(new ContractViolation(id, name, "STATUS_NOT_IN_SPEC",
                            "Expected status " + statusStr + " is not in spec response codes " + validStatusCodes));
                }
            }
        }

        return violations;
    }

    private Pattern buildPathPattern(String specPath) {
        String regex = specPath.replaceAll("\\{[^/]+}", "[^/]+");
        return Pattern.compile(regex);
    }

    private Set<String> parseSchemaFields(String requestBodySchema) {
        if (requestBodySchema == null || requestBodySchema.isBlank()) {
            return Set.of();
        }
        try {
            JsonNode root = MAPPER.readTree(requestBodySchema);
            JsonNode properties = root.get("properties");
            if (properties == null || !properties.isObject()) return Set.of();
            return Set.copyOf(List.copyOf(
                    java.util.stream.StreamSupport.stream(
                            java.util.Spliterators.spliteratorUnknownSize(
                                    properties.fieldNames(), java.util.Spliterator.ORDERED), false)
                            .toList()));
        } catch (Exception e) {
            return Set.of();
        }
    }
}
