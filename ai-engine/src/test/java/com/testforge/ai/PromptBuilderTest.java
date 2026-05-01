package com.testforge.ai;

import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.prompt.PromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private PromptBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PromptBuilder();
    }

    @Test
    void promptContainsMethodPathAndOperationId() {
        EndpointSpec spec = EndpointSpec.builder()
                .method("POST")
                .path("/api/payments")
                .operationId("createPayment")
                .summary("Create a payment")
                .requestBodySchema("{\"type\":\"object\"}")
                .responseSchemas(Map.of("201", "{\"type\":\"object\"}"))
                .build();

        String prompt = builder.build(spec);

        assertTrue(prompt.contains("POST"), "prompt must contain HTTP method");
        assertTrue(prompt.contains("/api/payments"), "prompt must contain path");
        assertTrue(prompt.contains("createPayment"), "prompt must contain operationId");
    }

    @Test
    void promptContainsRequestSchemaContent() {
        EndpointSpec spec = EndpointSpec.builder()
                .method("POST")
                .path("/api/payments")
                .operationId("createPayment")
                .summary("Create")
                .requestBodySchema("{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}")
                .responseSchemas(Map.of("201", "{}"))
                .build();

        String prompt = builder.build(spec);

        assertTrue(prompt.contains("amount"), "prompt must include schema field names");
    }

    @Test
    void promptSubstitutesNAWhenNoRequestBody() {
        EndpointSpec spec = EndpointSpec.builder()
                .method("GET")
                .path("/api/payments/{id}")
                .operationId("getPayment")
                .summary("Get payment")
                .requestBodySchema(null)
                .responseSchemas(Map.of("200", "{}", "404", "{}"))
                .build();

        String prompt = builder.build(spec);

        assertTrue(prompt.contains("N/A"), "GET endpoints must show N/A for request schema");
    }

    @Test
    void promptContainsJsonArrayOutputInstruction() {
        EndpointSpec spec = EndpointSpec.builder()
                .method("GET")
                .path("/items")
                .operationId("listItems")
                .summary("List")
                .requestBodySchema(null)
                .responseSchemas(Map.of("200", "{}"))
                .build();

        String prompt = builder.build(spec);

        assertTrue(prompt.contains("JSON array"), "prompt must instruct Claude to output a JSON array");
    }
}
