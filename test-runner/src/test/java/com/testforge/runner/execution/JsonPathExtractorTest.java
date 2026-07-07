package com.testforge.runner.execution;

import com.testforge.runner.model.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonPathExtractorTest {

    @Test
    void extractsActualStatusCodeFromHttpResponse() {
        HttpResponse response = new HttpResponse(202, Map.of("statusCode", 999), "", Map.of(), 10L);

        Object captured = JsonPathExtractor.extract(response, "$.statusCode");

        assertEquals(202, captured);
    }

    @Test
    void doesNotTreatStatusCodeAsBodyPathOrAlias() {
        HttpResponse response = new HttpResponse(202, Map.of("statusCode", 999), "", Map.of(), 10L);

        assertEquals(999, JsonPathExtractor.extract(response, "$.body.statusCode"));
        assertNull(JsonPathExtractor.extract(response, "$.status"));
        assertNull(JsonPathExtractor.extract(response, "$.statusCode.value"));
    }
}
