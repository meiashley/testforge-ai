package com.testforge.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RealClaudeClient implements ClaudeClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-sonnet-4-6";
    private static final int MAX_TOKENS = 16384;

    // TODO: Set to true and configure ANTHROPIC_API_KEY to enable real API calls
    private static final boolean ENABLED = true;

    private static final Path CACHE_DIR = Paths.get(".testforge-cache");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final String apiKey;

    public RealClaudeClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String generate(String prompt) {
        if (!ENABLED) {
            throw new UnsupportedOperationException(
                    "RealClaudeClient is not enabled. Set ENABLED=true and configure ANTHROPIC_API_KEY.");
        }

        String cacheKey = computeCacheKey(prompt);
        String cached = readCache(cacheKey);
        if (cached != null) {
            System.out.println("[cache hit] " + cacheKey.substring(0, 8));
            return cached;
        }
        System.out.println("[cache miss -> calling Claude] " + cacheKey.substring(0, 8));

        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(Map.of(
                    "model", MODEL,
                    "max_tokens", MAX_TOKENS,
                    "system", "You output only raw valid JSON. No markdown fences, no comments, no asterisks, no explanation. Your entire response must be parseable by JSON.parse().",
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
            String text = (String) first.get("text");
            writeCache(cacheKey, text);
            return text;
        } catch (IOException e) {
            throw new RuntimeException("Failed to call Claude API", e);
        }
    }

    private String computeCacheKey(String prompt) {
        String input = MODEL + ":" + prompt;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return new BigInteger(1, hash).toString(16);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute cache key", e);
        }
    }

    private String readCache(String key) {
        Path file = CACHE_DIR.resolve(key + ".txt");
        if (!Files.exists(file)) return null;
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private void writeCache(String key, String response) {
        try {
            Files.createDirectories(CACHE_DIR);
            Files.writeString(CACHE_DIR.resolve(key + ".txt"), response, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // cache write failure does not affect main flow
        }
    }
}
