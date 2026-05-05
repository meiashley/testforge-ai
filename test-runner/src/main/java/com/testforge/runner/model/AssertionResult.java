package com.testforge.runner.model;

import lombok.Value;

@Value
public class AssertionResult {
    String field;
    Object expected;
    Object actual;
    boolean passed;
    String failureReason;
}
