package com.testforge.ai.model;

import lombok.Data;

@Data
public class TestCase {
    private String id;
    private String name;
    private TestCaseType type;
    private Priority priority;
    private String scenario;
    private TestCaseRequest request;
    private TestCaseExpected expected;
    private String reasoning;
}
