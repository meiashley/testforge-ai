package com.testforge.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RealClaudeClient implements ClaudeClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-5";
    private static final int MAX_TOKENS = 4096;

    // TODO: Set to true and configure ANTHROPIC_API_KEY to enable real API calls
    private static final boolean ENABLED = false;

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;

    public RealClaudeClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String generate(String prompt) {
        if (!ENABLED) {
            throw new UnsupportedOperationException(
                    "RealClaudeClient is not enabled. Set ENABLED=true and configure ANTHROPIC_API_KEY.");
        }

        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(Map.of(
                    "model", MODEL,
                    "max_tokens", MAX_TOKENS,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Claude API request", e);
        }

        Request request = new Request.Builder()
                .url(API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            Map<?, ?> parsed = mapper.readValue(responseBody, Map.class);
            List<?> content = (List<?>) parsed.get("content");
            Map<?, ?> first = (Map<?, ?>) content.get(0);
            return (String) first.get("text");
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Claude API", e);
        }
    }
}
