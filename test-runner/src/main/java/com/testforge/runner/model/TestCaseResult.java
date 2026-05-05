package com.testforge.runner.model;

import com.testforge.ai.model.Priority;
import com.testforge.ai.model.TestCaseType;
import lombok.Value;

import java.util.List;

@Value
public class TestCaseResult {
    String testCaseId;
    String name;
    TestCaseType type;
    Priority priority;
    TestResultStatus status;
    HttpResponse httpResponse;
    List<AssertionResult> assertionResults;
    String failureCategory;
    long durationMs;
}
