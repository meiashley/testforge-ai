package com.testforge.ai.validation;

import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.model.TestCase;
import com.testforge.ai.model.TestCaseExpected;
import com.testforge.ai.model.TestCaseRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestCaseContractValidatorTest {

    private TestCaseContractValidator validator;

    private static final String PAYMENT_BODY_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "merchantId": {"type": "string"},
                "customerId": {"type": "string"},
                "amount": {"type": "number"},
                "currency": {"type": "string"},
                "description": {"type": "string"}
              },
              "required": ["merchantId", "customerId", "amount", "currency"]
            }
            """;

    private static final EndpointSpec POST_PAYMENTS_SPEC = EndpointSpec.builder()
            .method("POST")
            .path("/api/payments")
            .operationId("createPayment")
            .summary("Create payment")
            .requestBodySchema(PAYMENT_BODY_SCHEMA)
            .responseSchemas(Map.of("201", "{}", "400", "{}"))
            .build();

    private static final EndpointSpec GET_PAYMENT_SPEC = EndpointSpec.builder()
            .method("GET")
            .path("/api/payments/{id}")
            .operationId("getPayment")
            .summary("Get payment")
            .requestBodySchema(null)
            .responseSchemas(Map.of("200", "{}", "404", "{}"))
            .build();

    @BeforeEach
    void setUp() {
        validator = new TestCaseContractValidator();
    }

    @Test
    void validTest_returnsNoViolations() {
        TestCase test = buildTest("tc-1", "Happy path",
                "POST", "/api/payments",
                Map.of("merchantId", "m1", "customerId", "c1", "amount", 100, "currency", "CNY"),
                201);

        List<ContractViolation> violations = validator.validate(List.of(test), POST_PAYMENTS_SPEC);

        assertTrue(violations.isEmpty());
    }

    @Test
    void pathMismatch_returnsPathNotFoundViolation() {
        TestCase test = buildTest("tc-2", "Wrong path",
                "POST", "/api/wrong-endpoint",
                Map.of("merchantId", "m1", "customerId", "c1", "amount", 50, "currency", "USD"),
                201);

        List<ContractViolation> violations = validator.validate(List.of(test), POST_PAYMENTS_SPEC);

        assertEquals(1, violations.size());
        assertEquals("PATH_NOT_FOUND", violations.get(0).getViolationType());
        assertEquals("tc-2", violations.get(0).getTestCaseId());
    }

    @Test
    void methodMismatch_returnsMethodMismatchViolation() {
        TestCase test = buildTest("tc-3", "Wrong method",
                "GET", "/api/payments",
                null,
                201);

        List<ContractViolation> violations = validator.validate(List.of(test), POST_PAYMENTS_SPEC);

        assertEquals(1, violations.size());
        assertEquals("METHOD_MISMATCH", violations.get(0).getViolationType());
    }

    @Test
    void statusNotInSpec_returnsStatusNotInSpecViolation() {
        TestCase test = buildTest("tc-4", "Invalid status",
                "POST", "/api/payments",
                Map.of("merchantId", "m1", "customerId", "c1", "amount", 10, "currency", "CNY"),
                200);

        List<ContractViolation> violations = validator.validate(List.of(test), POST_PAYMENTS_SPEC);

        assertEquals(1, violations.size());
        assertEquals("STATUS_NOT_IN_SPEC", violations.get(0).getViolationType());
        assertTrue(violations.get(0).getDetails().contains("200"));
    }

    @Test
    void unknownBodyField_returnsFieldNotInSchemaViolation() {
        TestCase test = buildTest("tc-5", "Unknown body field",
                "POST", "/api/payments",
                Map.of("merchantId", "m1", "customerId", "c1", "amount", 10, "currency", "CNY",
                        "unknownField", "oops"),
                201);

        List<ContractViolation> violations = validator.validate(List.of(test), POST_PAYMENTS_SPEC);

        assertEquals(1, violations.size());
        assertEquals("FIELD_NOT_IN_SCHEMA", violations.get(0).getViolationType());
        assertTrue(violations.get(0).getDetails().contains("unknownField"));
    }

    @Test
    void pathWithPlaceholder_matchesConcreteValue() {
        TestCase test = buildTest("tc-6", "Get by ID",
                "GET", "/api/payments/pay_abc123",
                null,
                200);

        List<ContractViolation> violations = validator.validate(List.of(test), GET_PAYMENT_SPEC);

        assertTrue(violations.isEmpty());
    }

    @Test
    void multipleTests_onlyViolatingOnesReported() {
        TestCase valid = buildTest("tc-7", "Valid", "POST", "/api/payments",
                Map.of("merchantId", "m1", "customerId", "c1", "amount", 10, "currency", "CNY"), 201);
        TestCase invalid = buildTest("tc-8", "Bad status", "POST", "/api/payments",
                Map.of("merchantId", "m1", "customerId", "c1", "amount", 10, "currency", "CNY"), 500);

        List<ContractViolation> violations = validator.validate(List.of(valid, invalid), POST_PAYMENTS_SPEC);

        assertEquals(1, violations.size());
        assertEquals("tc-8", violations.get(0).getTestCaseId());
    }

    private TestCase buildTest(String id, String name, String method, String path,
                                Map<String, Object> body, int status) {
        TestCaseRequest req = new TestCaseRequest();
        req.setMethod(method);
        req.setPath(path);
        req.setBody(body);

        TestCaseExpected exp = new TestCaseExpected();
        exp.setStatus(status);

        TestCase tc = new TestCase();
        tc.setId(id);
        tc.setName(name);
        tc.setRequest(req);
        tc.setExpected(exp);
        return tc;
    }
}
