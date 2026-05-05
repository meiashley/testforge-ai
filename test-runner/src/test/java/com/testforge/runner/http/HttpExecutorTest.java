package com.testforge.runner.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.testforge.ai.model.TestCaseRequest;
import com.testforge.runner.model.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpExecutorTest {

    private WireMockServer wireMock;
    private HttpExecutor executor;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        executor = new HttpExecutor();
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void successfulJsonResponse_isParsedIntoBody() {
        wireMock.stubFor(get(urlEqualTo("/payments/pay_123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"pay_123\",\"status\":\"COMPLETED\"}")));

        TestCaseRequest request = new TestCaseRequest();
        request.setMethod("GET");
        request.setPath("/payments/pay_123");

        HttpResponse response = executor.execute(request, "http://localhost:" + wireMock.port());

        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("pay_123", response.getBody().get("id"));
        assertEquals("COMPLETED", response.getBody().get("status"));
        assertNotNull(response.getRawBody());
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    void nonJsonResponse_setsRawBodyAndNullBody() {
        wireMock.stubFor(get(urlEqualTo("/health"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("OK")));

        TestCaseRequest request = new TestCaseRequest();
        request.setMethod("GET");
        request.setPath("/health");

        HttpResponse response = executor.execute(request, "http://localhost:" + wireMock.port());

        assertEquals(200, response.getStatusCode());
        assertNull(response.getBody());
        assertEquals("OK", response.getRawBody());
        assertTrue(response.getDurationMs() >= 0);
    }
}
