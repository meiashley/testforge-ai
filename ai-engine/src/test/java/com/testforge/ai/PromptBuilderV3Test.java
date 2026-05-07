package com.testforge.ai;

import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.prompt.PromptBuilderV3;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderV3Test {

    private PromptBuilderV3 builder;

    private EndpointSpec createPaymentSpec() {
        return EndpointSpec.builder()
                .method("POST")
                .path("/api/payments")
                .operationId("createPayment")
                .summary("Create a payment")
                .requestBodySchema("{\"type\":\"object\",\"properties\":{\"merchantId\":{\"type\":\"string\"}}}")
                .responseSchemas(Map.of("201", "{\"type\":\"object\"}", "400", "{\"type\":\"object\"}"))
                .build();
    }

    private EndpointSpec getPaymentSpec() {
        return EndpointSpec.builder()
                .method("GET")
                .path("/api/payments/{id}")
                .operationId("getPayment")
                .summary("Get payment by ID")
                .requestBodySchema(null)
                .responseSchemas(Map.of("200", "{\"type\":\"object\"}", "404", "{\"type\":\"object\"}"))
                .build();
    }

    private EndpointSpec refundSpec() {
        return EndpointSpec.builder()
                .method("POST")
                .path("/api/payments/{id}/refund")
                .operationId("refundPayment")
                .summary("Refund a payment")
                .requestBodySchema("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}")
                .responseSchemas(Map.of("200", "{\"type\":\"object\"}", "422", "{\"type\":\"object\"}"))
                .build();
    }

    @BeforeEach
    void setUp() {
        builder = new PromptBuilderV3();
    }

    @Test
    void promptContainsPaymentIdPlaceholder() {
        String prompt = builder.build(getPaymentSpec());

        assertTrue(prompt.contains("{{paymentId}}"),
                "V3 prompt must contain {{paymentId}} placeholder");
    }

    @Test
    void promptContainsTestFixturesAvailableSection() {
        String prompt = builder.build(getPaymentSpec());

        assertTrue(prompt.contains("TEST FIXTURES AVAILABLE"),
                "V3 prompt must include TEST FIXTURES AVAILABLE section");
    }

    @Test
    void promptContainsV2StateMachineContext() {
        String prompt = builder.build(createPaymentSpec());

        assertTrue(prompt.contains("COMPLETED"), "V3 prompt must retain COMPLETED state");
        assertTrue(prompt.contains("REFUNDED"), "V3 prompt must retain REFUNDED state");
        assertTrue(prompt.contains("PARTIALLY_REFUNDED"), "V3 prompt must retain PARTIALLY_REFUNDED state");
        assertTrue(prompt.contains("422"), "V3 prompt must retain 422 for double-refund scenario");
    }

    @Test
    void promptContainsEndpointMethodAndPath() {
        EndpointSpec spec = createPaymentSpec();
        String prompt = builder.build(spec);

        assertTrue(prompt.contains("POST"), "V3 prompt must contain HTTP method");
        assertTrue(prompt.contains("/api/payments"), "V3 prompt must contain endpoint path");
        assertTrue(prompt.contains("createPayment"), "V3 prompt must contain operationId");
    }

    @Test
    void promptInstructsNotToHardcodePaymentIds() {
        String prompt = builder.build(getPaymentSpec());

        assertTrue(prompt.contains("Do not hardcode payment IDs"),
                "V3 prompt must instruct against hardcoding payment IDs");
    }

    @Test
    void promptContainsPlaceholderGuidanceForGetAndRefundPaths() {
        String prompt = builder.build(refundSpec());

        assertTrue(prompt.contains("/api/payments/{{paymentId}}/refund"),
                "V3 prompt must show refund path example with {{paymentId}}");
        assertTrue(prompt.contains("/api/payments/{{paymentId}}"),
                "V3 prompt must show GET path example with {{paymentId}}");
    }

    @Test
    void promptContainsFakeIdGuidanceForNegativeTests() {
        String prompt = builder.build(getPaymentSpec());

        assertTrue(prompt.contains("non-existent-id") || prompt.contains("does-not-exist"),
                "V3 prompt must still instruct use of fake IDs for NEGATIVE/SECURITY tests");
    }
}
