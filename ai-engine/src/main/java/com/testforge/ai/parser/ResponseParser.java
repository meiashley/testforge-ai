package com.testforge.ai.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.ai.model.TestCase;

import java.util.List;

public class ResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<TestCase> parse(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<TestCase>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Claude response as List<TestCase>: " + e.getMessage(), e);
        }
    }
}
