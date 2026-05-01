package com.testforge.ai.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.testforge.ai.model.EndpointSpec;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SwaggerOpenApiLoader implements OpenApiLoader {

    @Override
    public List<EndpointSpec> parse(String yamlContent) {
        ParseOptions opts = new ParseOptions();
        opts.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(yamlContent, null, opts);

        if (result.getOpenAPI() == null) {
            throw new IllegalArgumentException(
                    "Invalid OpenAPI YAML. Parser messages: " + result.getMessages());
        }

        OpenAPI openAPI = result.getOpenAPI();
        List<EndpointSpec> specs = new ArrayList<>();

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem item = pathEntry.getValue();

            if (item.getPost() != null)   specs.add(build("POST",   path, item.getPost()));
            if (item.getGet() != null)    specs.add(build("GET",    path, item.getGet()));
            if (item.getPut() != null)    specs.add(build("PUT",    path, item.getPut()));
            if (item.getPatch() != null)  specs.add(build("PATCH",  path, item.getPatch()));
            if (item.getDelete() != null) specs.add(build("DELETE", path, item.getDelete()));
        }

        return specs;
    }

    private EndpointSpec build(String method, String path, Operation op) {
        return EndpointSpec.builder()
                .method(method)
                .path(path)
                .operationId(op.getOperationId())
                .summary(op.getSummary())
                .requestBodySchema(extractRequestBodySchema(op))
                .responseSchemas(extractResponseSchemas(op))
                .build();
    }

    private String extractRequestBodySchema(Operation op) {
        if (op.getRequestBody() == null) return null;
        var content = op.getRequestBody().getContent();
        if (content == null || !content.containsKey("application/json")) return null;
        Schema<?> schema = content.get("application/json").getSchema();
        if (schema == null) return null;
        return toJson(schema);
    }

    private Map<String, String> extractResponseSchemas(Operation op) {
        Map<String, String> result = new LinkedHashMap<>();
        if (op.getResponses() == null) return result;
        for (Map.Entry<String, ApiResponse> entry : op.getResponses().entrySet()) {
            String code = entry.getKey();
            ApiResponse response = entry.getValue();
            if (response.getContent() == null || !response.getContent().containsKey("application/json")) {
                continue;
            }
            Schema<?> schema = response.getContent().get("application/json").getSchema();
            if (schema != null) result.put(code, toJson(schema));
        }
        return result;
    }

    private String toJson(Schema<?> schema) {
        try {
            return Json.mapper().writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
