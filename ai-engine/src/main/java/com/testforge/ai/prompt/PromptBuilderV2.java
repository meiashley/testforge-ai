package com.testforge.ai.prompt;

import com.testforge.ai.model.EndpointSpec;

import java.util.stream.Collectors;

public class PromptBuilderV2 implements EndpointPromptBuilder {

    private static final String STATE_MACHINE_CONTEXT = """
            API Business State Machine (authoritative — use these to set exact expected values):
            - POST /api/payments (success) → payment.status = COMPLETED
            - POST /api/payments/{id}/refund with amount < payment.amount (partial) → payment.status = PARTIALLY_REFUNDED
            - POST /api/payments/{id}/refund with amount = payment.amount (full) → payment.status = REFUNDED
            - POST /api/payments/{id}/refund on already-REFUNDED payment → HTTP 422
            - GET /api/payments/{id} with non-existent id → HTTP 404
            """;

    private static final String FIELD_GUIDANCE = """
            Field assertion rules:
            - Dynamic fields (value unpredictable) → use "non-null": id, createdAt, updatedAt
            - Deterministic fields (value known from input or state machine) → use exact value: status, amount, currency, customerId, merchantId, description
            - Path parameters with dynamic IDs in negative/not-found tests → use obviously-fake values like "non-existent-id" or "does-not-exist", never guess a real ID
            """;

    private static final String FEW_SHOT_EXAMPLE = """
            Example of an ideal HAPPY_PATH test case for POST /api/payments:
            {
              "id": "tc-001",
              "name": "Create payment with all required fields",
              "type": "HAPPY_PATH",
              "priority": "P0",
              "scenario": "Given a valid payment request When POST /api/payments is called Then a 201 response is returned with status COMPLETED",
              "request": {
                "method": "POST",
                "path": "/api/payments",
                "headers": {"Content-Type": "application/json"},
                "body": {
                  "merchantId": "merchant-001",
                  "customerId": "customer-abc",
                  "amount": 100.00,
                  "currency": "USD"
                }
              },
              "expected": {
                "status": 201,
                "bodyAssertions": {
                  "id": "non-null",
                  "merchantId": "merchant-001",
                  "customerId": "customer-abc",
                  "amount": 100.00,
                  "currency": "USD",
                  "status": "COMPLETED",
                  "createdAt": "non-null",
                  "updatedAt": "non-null"
                }
              },
              "reasoning": "Verifies the core happy path: payment is created and immediately transitions to COMPLETED per the state machine."
            }
            """;

    private static final String TEMPLATE = """
            You are an expert API test engineer. Given the following REST API endpoint specification,
            generate EXACTLY 3 test cases covering at least 3 of these types:
            - HAPPY_PATH (valid request, expected success)
            - BOUNDARY (edge values like minimum/maximum amount, expected success or 4xx)
            - NEGATIVE (missing/invalid field, expected 4xx)
            - SECURITY (injection or obviously-fake ID, expected 4xx)
            Choose the 3 most relevant types for this endpoint.

            {state_machine_context}

            {field_guidance}

            {few_shot_example}

            Endpoint: {endpoint_method} {endpoint_path}
            Operation ID: {operationId}

            Request Schema:
            {request_schema}

            Response Schemas:
            {response_schemas}

            Output ONLY a valid JSON array with exactly 3 elements. Each element must be a test case object with exactly these fields:
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

            Do not include any explanation, markdown, or text outside the JSON array. Output exactly 3 test cases covering at least 3 different types from: HAPPY_PATH, BOUNDARY, NEGATIVE, SECURITY.
            """;

    public String build(EndpointSpec spec) {
        String requestSchema = spec.getRequestBodySchema() != null
                ? spec.getRequestBodySchema()
                : "N/A";

        String responseSchemas = spec.getResponseSchemas().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));

        return TEMPLATE
                .replace("{state_machine_context}", STATE_MACHINE_CONTEXT)
                .replace("{field_guidance}", FIELD_GUIDANCE)
                .replace("{few_shot_example}", FEW_SHOT_EXAMPLE)
                .replace("{endpoint_method}", spec.getMethod())
                .replace("{endpoint_path}", spec.getPath())
                .replace("{operationId}", spec.getOperationId())
                .replace("{request_schema}", requestSchema)
                .replace("{response_schemas}", responseSchemas);
    }
}
