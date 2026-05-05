package com.testforge.runner.model;

import lombok.Value;

import java.util.Map;

@Value
public class HttpResponse {
    int statusCode;
    Map<String, Object> body;
    String rawBody;
    Map<String, String> headers;
    long durationMs;
}
