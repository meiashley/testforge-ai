package com.testforge.ai.model;

import lombok.Data;
import java.util.Map;

@Data
public class TestCaseExpected {
    private int status;
    private Map<String, Object> bodyAssertions;
}
