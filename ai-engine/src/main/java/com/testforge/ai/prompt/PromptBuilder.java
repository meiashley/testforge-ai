package com.testforge.ai.prompt;

import com.testforge.ai.model.EndpointSpec;

import java.util.stream.Collectors;

public class PromptBuilder {

    private static final String TEMPLATE = """
            You are an expert API test engineer. Given the following REST API endpoint specification,
            generate comprehensive test scenarios covering:
            1. Happy path (functional tests)
            2. Boundary value analysis
            3. Exception / error cases
            4. Security edge cases (injection, oversized payloads, etc.)

            Endpoint: {endpoint_method} {endpoint_path}
            Operation ID: {operationId}

            Request Schema:
            {request_schema}

            Response Schemas:
            {response_schemas}

            Output ONLY a valid JSON array. Each element must be a test case object with exactly these fields:
            {
              "id": "tc-001",
              "name": "short descriptive name",
              "type": "HAPPY_PATH | BOUNDARY | NEGATIVE | SECURITY",
              "priority": "P0 | P1 | P2",
              "scenario": "Given ... When ... Then ...",
              "request": {
                "method": "POST",
                "path": "/api/payments",
                "headers": {"Content-Type": "application/json"},
                "body": {}
              },
              "expected": {
                "status": 201,
                "bodyAssertions": {"id": "non-null"}
              },
              "reasoning": "why this test case is important"
            }

            Do not include any explanation, markdown, or text outside the JSON array.
            """;

    public String build(EndpointSpec spec) {
        String requestSchema = spec.getRequestBodySchema() != null
                ? spec.getRequestBodySchema()
                : "N/A";

        String responseSchemas = spec.getResponseSchemas().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));

        return TEMPLATE
                .replace("{endpoint_method}", spec.getMethod())
                .replace("{endpoint_path}", spec.getPath())
                .replace("{operationId}", spec.getOperationId())
                .replace("{request_schema}", requestSchema)
                .replace("{response_schemas}", responseSchemas);
    }
}
