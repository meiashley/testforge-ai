package com.testforge.ai;

import com.testforge.ai.model.EndpointSpec;
import com.testforge.ai.pipeline.OpenApiLoader;
import com.testforge.ai.pipeline.SwaggerOpenApiLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenApiLoaderTest {

    private OpenApiLoader loader;

    @BeforeEach
    void setUp() {
        loader = new SwaggerOpenApiLoader();
    }

    @Test
    void parsesPostAndGetEndpointsFromMinimalSpec() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Test API
                  version: 1.0.0
                paths:
                  /items:
                    post:
                      operationId: createItem
                      summary: Create item
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              properties:
                                name:
                                  type: string
                      responses:
                        '201':
                          description: Created
                          content:
                            application/json:
                              schema:
                                type: object
                  /items/{id}:
                    get:
                      operationId: getItem
                      summary: Get item
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema:
                            type: string
                      responses:
                        '200':
                          description: Found
                        '404':
                          description: Not found
                """;

        List<EndpointSpec> specs = loader.parse(yaml);

        assertEquals(2, specs.size());

        EndpointSpec post = specs.stream()
                .filter(s -> s.getMethod().equals("POST"))
                .findFirst()
                .orElseThrow();
        assertEquals("createItem", post.getOperationId());
        assertEquals("/items", post.getPath());
        assertNotNull(post.getRequestBodySchema(), "POST requestBodySchema must not be null");
        assertTrue(post.getResponseSchemas().containsKey("201"));
    }

    @Test
    void setsRequestBodySchemaToNullForGetEndpoint() {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Test API
                  version: 1.0.0
                paths:
                  /items/{id}:
                    get:
                      operationId: getItem
                      summary: Get item
                      parameters:
                        - name: id
                          in: path
                          required: true
                          schema:
                            type: string
                      responses:
                        '200':
                          description: Found
                """;

        List<EndpointSpec> specs = loader.parse(yaml);

        assertEquals(1, specs.size());
        assertNull(specs.get(0).getRequestBodySchema());
    }

    @Test
    void throwsIllegalArgumentExceptionForMalformedYaml() {
        String badYaml = "this is not: valid: openapi: [unclosed";

        assertThrows(IllegalArgumentException.class, () -> loader.parse(badYaml));
    }
}
