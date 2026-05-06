package com.testforge.ai;

import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.prompt.PromptBuilderV2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderV2Test {

    private PromptBuilderV2 builder;

    private EndpointSpec createPaymentSpec() {
        return EndpointSpec.builder()
                .method("POST")
                .path("/api/payments")
                .operationId("createPayment")
                .summary("Create a payment")
                .requestBodySchema("{\"type\":\"object\",\"properties\":{\"merchantId\":{\"type\":\"string\"},\"customerId\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"},\"currency\":{\"type\":\"string\"}}}")
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

    @BeforeEach
    void setUp() {
        builder = new PromptBuilderV2();
    }

    @Test
    void promptContainsStateMachineContext() {
        String prompt = builder.build(createPaymentSpec());

        assertTrue(prompt.contains("COMPLETED"), "V2 prompt must state POST /payments → COMPLETED");
        assertTrue(prompt.contains("REFUNDED"), "V2 prompt must include REFUNDED state");
        assertTrue(prompt.contains("PARTIALLY_REFUNDED"), "V2 prompt must include PARTIALLY_REFUNDED state");
        assertTrue(prompt.contains("422"), "V2 prompt must include 422 for double-refund");
    }

    @Test
    void promptContainsFieldGuidanceWithNonNull() {
        String prompt = builder.build(createPaymentSpec());

        assertTrue(prompt.contains("non-null"), "V2 prompt must instruct use of non-null for dynamic fields");
        assertTrue(prompt.contains("createdAt"), "V2 prompt must name createdAt as a dynamic field");
        assertTrue(prompt.contains("updatedAt"), "V2 prompt must name updatedAt as a dynamic field");
    }

    @Test
    void promptContainsFewShotExampleWithCorrectStatus() {
        String prompt = builder.build(createPaymentSpec());

        assertTrue(prompt.contains("COMPLETED"), "Few-shot example must show COMPLETED as expected status");
        assertTrue(prompt.contains("\"status\": \"COMPLETED\""), "Few-shot example must use exact COMPLETED value in bodyAssertions");
    }

    @Test
    void promptContainsNonExistentIdGuidanceForNotFoundTests() {
        String prompt = builder.build(getPaymentSpec());

        assertTrue(prompt.contains("non-existent-id") || prompt.contains("does-not-exist"),
                "V2 prompt must instruct use of obviously-fake IDs for not-found tests");
        assertTrue(prompt.contains("404"), "V2 prompt must reference 404 for non-existent IDs");
    }

    @Test
    void promptRequiresAtLeastThreeDifferentTypes() {
        String prompt = builder.build(createPaymentSpec());

        assertTrue(prompt.contains("at least 3"), "V2 prompt must require at least 3 different types");
        assertTrue(prompt.contains("HAPPY_PATH") && prompt.contains("BOUNDARY")
                && prompt.contains("NEGATIVE") && prompt.contains("SECURITY"),
                "V2 prompt must list all four possible types");
    }

    @Test
    void promptStillContainsEndpointDetails() {
        EndpointSpec spec = createPaymentSpec();
        String prompt = builder.build(spec);

        assertTrue(prompt.contains("POST"), "prompt must contain HTTP method");
        assertTrue(prompt.contains("/api/payments"), "prompt must contain path");
        assertTrue(prompt.contains("createPayment"), "prompt must contain operationId");
        assertTrue(prompt.contains("JSON array"), "prompt must instruct Claude to output a JSON array");
    }

    @Test
    void promptUsesNAForGetEndpointWithNoRequestBody() {
        String prompt = builder.build(getPaymentSpec());

        assertTrue(prompt.contains("N/A"), "GET endpoints must show N/A for request schema");
    }
}
