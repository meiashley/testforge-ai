package com.testforge.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TestCaseRequest {
    private String method;
    private String path;
    private Map<String, String> headers;
    private Map<String, Object> body;
}
