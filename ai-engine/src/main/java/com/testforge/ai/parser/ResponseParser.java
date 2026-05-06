package com.testforge.ai.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.model.TestCase;

import java.util.List;

public class ResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<TestCase> parse(String json) {
        try {
            String cleaned = stripMarkdownFence(json);
            return MAPPER.readValue(cleaned, new TypeReference<List<TestCase>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response as List<TestCase>: " + e.getMessage(), e);
        }
    }

    private String stripMarkdownFence(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                String body = trimmed.substring(firstNewline + 1);
                if (body.endsWith("```")) {
                    body = body.substring(0, body.length() - 3);
                }
                return body.strip();
            }
        }
        return trimmed;
    }
}
