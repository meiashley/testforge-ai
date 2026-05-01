package com.testforge.ai.model;

import lombok.Builder;
import lombok.Value;
import java.util.Map;

@Value
@Builder
public class EndpointSpec {
    String method;
    String path;
    String operationId;
    String summary;
    String requestBodySchema;
    Map<String, String> responseSchemas;
}
