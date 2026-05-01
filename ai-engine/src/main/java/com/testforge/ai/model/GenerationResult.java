package com.testforge.ai.model;

import lombok.Value;
import java.util.List;

@Value
public class GenerationResult {
    EndpointSpec endpoint;
    List<TestCase> testCases;
}
