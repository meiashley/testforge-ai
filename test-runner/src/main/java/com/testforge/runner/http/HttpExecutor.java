package com.testforge.runner.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.model.TestCaseRequest;
import com.testforge.runner.model.HttpResponse;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class HttpExecutor {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient();

    public HttpResponse execute(TestCaseRequest request, String baseUrl) {
        String url = baseUrl + request.getPath();

        Request.Builder builder = new Request.Builder().url(url);

        if (request.getHeaders() != null) {
            request.getHeaders().forEach(builder::addHeader);
        }

        String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
        RequestBody requestBody = buildRequestBody(method, request.getBody());
        builder.method(method, requestBody);

        long start = System.currentTimeMillis();
        try (Response okResponse = client.newCall(builder.build()).execute()) {
            long durationMs = System.currentTimeMillis() - start;

            String rawBody = okResponse.body() != null ? okResponse.body().string() : "";
            Map<String, Object> parsedBody = tryParseJson(rawBody);

            Map<String, String> headers = new HashMap<>();
            okResponse.headers().forEach(pair -> headers.put(pair.getFirst(), pair.getSecond()));

            return new HttpResponse(okResponse.code(), parsedBody, rawBody, headers, durationMs);
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed: " + method + " " + url, e);
        }
    }

    public HttpResponse execute(String method, String url, Map<String, String> headers, String body) {
        Request.Builder builder = new Request.Builder().url(url);

        if (headers != null) {
            headers.forEach(builder::addHeader);
        }

        String upperMethod = method == null ? "GET" : method.toUpperCase();
        RequestBody requestBody = buildStringRequestBody(upperMethod, body);
        builder.method(upperMethod, requestBody);

        long start = System.currentTimeMillis();
        try (Response okResponse = client.newCall(builder.build()).execute()) {
            long durationMs = System.currentTimeMillis() - start;

            String rawBody = okResponse.body() != null ? okResponse.body().string() : "";
            Map<String, Object> parsedBody = tryParseJson(rawBody);

            Map<String, String> responseHeaders = new HashMap<>();
            okResponse.headers().forEach(pair -> responseHeaders.put(pair.getFirst(), pair.getSecond()));

            return new HttpResponse(okResponse.code(), parsedBody, rawBody, responseHeaders, durationMs);
        } catch (IOException e) {
            throw new RuntimeException("HTTP request failed: " + upperMethod + " " + url, e);
        }
    }

    private RequestBody buildStringRequestBody(String method, String body) {
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            return null;
        }
        if (body == null) {
            return RequestBody.create("", JSON);
        }
        return RequestBody.create(body, JSON);
    }

    private RequestBody buildRequestBody(String method, Map<String, Object> body) {
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            return null;
        }
        if (body == null) {
            return RequestBody.create("", JSON);
        }
        try {
            return RequestBody.create(MAPPER.writeValueAsString(body), JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize request body", e);
        }
    }

    private Map<String, Object> tryParseJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
