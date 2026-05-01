package com.testforge.ai.client;

public interface ClaudeClient {
    /**
     * Sends a prompt and returns the raw response string (expected to be a JSON array).
     */
    String generate(String prompt);
}
