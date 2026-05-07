package com.testforge.runner.setup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SetupRunner {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient();

    public Map<String, String> run(String baseUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("merchantId", "setup-merchant");
        body.put("customerId", "setup-customer");
        body.put("amount", 100.00);
        body.put("currency", "USD");

        String json;
        try {
            json = MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize setup request body", e);
        }

        Request request = new Request.Builder()
                .url(baseUrl + "/api/payments")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String rawBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Setup POST /api/payments failed with status " + response.code() + ": " + rawBody);
            }
            Map<String, Object> parsed = MAPPER.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
            Object id = parsed.get("id");
            if (id == null) {
                throw new RuntimeException("Setup response missing 'id' field: " + rawBody);
            }
            Map<String, String> fixtures = new HashMap<>();
            fixtures.put("paymentId", id.toString());
            return fixtures;
        } catch (IOException e) {
            throw new RuntimeException("Setup request failed: POST " + baseUrl + "/api/payments", e);
        }
    }
}
