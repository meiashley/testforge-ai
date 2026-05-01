package com.testforge.ai.client;

import java.util.Map;

public class MockClaudeClient implements ClaudeClient {

    private static final String CREATE_PAYMENT_JSON = """
            [
              {
                "id": "tc-001",
                "name": "Create payment with valid CNY amount",
                "type": "HAPPY_PATH",
                "priority": "P0",
                "scenario": "Given a valid payment request with amount 128.50 CNY\\nWhen POST /api/payments is called\\nThen response status is 201\\nAnd response body contains a non-null payment id\\nAnd status is COMPLETED",
                "request": {
                  "method": "POST",
                  "path": "/api/payments",
                  "headers": {"Content-Type": "application/json"},
                  "body": {"merchantId": "merchant-001", "customerId": "customer-abc", "amount": 128.50, "currency": "CNY"}
                },
                "expected": {"status": 201, "bodyAssertions": {"id": "non-null", "status": "COMPLETED"}},
                "reasoning": "Validates the core happy path for payment creation with all required fields."
              },
              {
                "id": "tc-002",
                "name": "Create payment with minimum allowed amount",
                "type": "BOUNDARY",
                "priority": "P1",
                "scenario": "Given a payment request with amount 0.01 (minimum)\\nWhen POST /api/payments is called\\nThen response status is 201",
                "request": {
                  "method": "POST",
                  "path": "/api/payments",
                  "headers": {"Content-Type": "application/json"},
                  "body": {"merchantId": "merchant-001", "customerId": "customer-abc", "amount": 0.01, "currency": "USD"}
                },
                "expected": {"status": 201, "bodyAssertions": {"status": "COMPLETED"}},
                "reasoning": "Boundary: minimum valid amount per schema (minimum: 0.01)."
              },
              {
                "id": "tc-003",
                "name": "Create payment with zero amount is rejected",
                "type": "NEGATIVE",
                "priority": "P0",
                "scenario": "Given a payment request with amount 0\\nWhen POST /api/payments is called\\nThen response status is 400\\nAnd error code is VALIDATION_ERROR",
                "request": {
                  "method": "POST",
                  "path": "/api/payments",
                  "headers": {"Content-Type": "application/json"},
                  "body": {"merchantId": "merchant-001", "customerId": "customer-abc", "amount": 0, "currency": "USD"}
                },
                "expected": {"status": 400, "bodyAssertions": {"code": "VALIDATION_ERROR"}},
                "reasoning": "Amount must be > 0 per schema; zero should be rejected with 400."
              }
            ]
            """;

    private static final String GET_PAYMENT_JSON = """
            [
              {
                "id": "tc-004",
                "name": "Get existing payment by valid ID",
                "type": "HAPPY_PATH",
                "priority": "P0",
                "scenario": "Given a payment with id pay_a1b2c3d4e5f6 exists\\nWhen GET /api/payments/pay_a1b2c3d4e5f6 is called\\nThen response status is 200\\nAnd response body contains the payment details",
                "request": {
                  "method": "GET",
                  "path": "/api/payments/pay_a1b2c3d4e5f6",
                  "headers": {},
                  "body": null
                },
                "expected": {"status": 200, "bodyAssertions": {"id": "pay_a1b2c3d4e5f6", "status": "non-null"}},
                "reasoning": "Core happy path: retrieve a payment that exists."
              },
              {
                "id": "tc-005",
                "name": "Get payment with non-existent ID returns 404",
                "type": "NEGATIVE",
                "priority": "P0",
                "scenario": "Given no payment with id pay_doesnotexist exists\\nWhen GET /api/payments/pay_doesnotexist is called\\nThen response status is 404",
                "request": {
                  "method": "GET",
                  "path": "/api/payments/pay_doesnotexist",
                  "headers": {},
                  "body": null
                },
                "expected": {"status": 404, "bodyAssertions": {}},
                "reasoning": "Standard not-found case per OpenAPI 404 response spec."
              },
              {
                "id": "tc-006",
                "name": "Get payment with SQL injection in ID",
                "type": "SECURITY",
                "priority": "P1",
                "scenario": "Given an ID containing SQL injection payload\\nWhen GET /api/payments/{injected-id} is called\\nThen response status is 404 or 400\\nAnd no stack trace is exposed in response body",
                "request": {
                  "method": "GET",
                  "path": "/api/payments/1-OR-1-eq-1",
                  "headers": {},
                  "body": null
                },
                "expected": {"status": 404, "bodyAssertions": {}},
                "reasoning": "Security: ensure injection payloads in path parameters are handled safely."
              }
            ]
            """;

    private static final String REFUND_PAYMENT_JSON = """
            [
              {
                "id": "tc-007",
                "name": "Full refund of a completed payment",
                "type": "HAPPY_PATH",
                "priority": "P0",
                "scenario": "Given a COMPLETED payment with id pay_a1b2c3d4e5f6\\nWhen POST /api/payments/pay_a1b2c3d4e5f6/refund with reason only\\nThen response status is 200\\nAnd payment status is REFUNDED",
                "request": {
                  "method": "POST",
                  "path": "/api/payments/pay_a1b2c3d4e5f6/refund",
                  "headers": {"Content-Type": "application/json"},
                  "body": {"reason": "Customer requested cancellation"}
                },
                "expected": {"status": 200, "bodyAssertions": {"status": "REFUNDED"}},
                "reasoning": "Core happy path: full refund omitting optional amount field."
              },
              {
                "id": "tc-008",
                "name": "Partial refund with valid amount",
                "type": "HAPPY_PATH",
                "priority": "P1",
                "scenario": "Given a COMPLETED payment of 128.50\\nWhen POST /api/payments/{id}/refund with amount 50.00\\nThen response status is 200\\nAnd status is PARTIALLY_REFUNDED",
                "request": {
                  "method": "POST",
                  "path": "/api/payments/pay_a1b2c3d4e5f6/refund",
                  "headers": {"Content-Type": "application/json"},
                  "body": {"amount": 50.00, "reason": "Partial order cancellation"}
                },
                "expected": {"status": 200, "bodyAssertions": {"status": "PARTIALLY_REFUNDED"}},
                "reasoning": "Partial refund happy path."
              },
              {
                "id": "tc-009",
                "name": "Refund a non-refundable payment returns 422",
                "type": "NEGATIVE",
                "priority": "P0",
                "scenario": "Given a payment in REFUNDED state\\nWhen POST /api/payments/{id}/refund is called\\nThen response status is 422",
                "request": {
                  "method": "POST",
                  "path": "/api/payments/pay_already_refunded/refund",
                  "headers": {"Content-Type": "application/json"},
                  "body": {"reason": "double refund attempt"}
                },
                "expected": {"status": 422, "bodyAssertions": {}},
                "reasoning": "Business rule: only COMPLETED payments can be refunded; 422 for invalid state transitions."
              }
            ]
            """;

    private static final Map<String, String> RESPONSES = Map.of(
            "createPayment",  CREATE_PAYMENT_JSON,
            "getPayment",     GET_PAYMENT_JSON,
            "refundPayment",  REFUND_PAYMENT_JSON
    );

    @Override
    public String generate(String prompt) {
        return RESPONSES.entrySet().stream()
                .filter(e -> prompt.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("[]");
    }
}
