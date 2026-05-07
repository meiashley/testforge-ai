package com.testforge.runner.setup;

import com.testforge.mockbank.MockBankingApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = MockBankingApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SetupRunnerTest {

    @LocalServerPort
    int port;

    @Test
    void returnsFixturesWithPaymentId() {
        String baseUrl = "http://localhost:" + port;
        Map<String, String> fixtures = new SetupRunner().run(baseUrl);

        assertTrue(fixtures.containsKey("paymentId"), "fixtures must contain 'paymentId'");
        String paymentId = fixtures.get("paymentId");
        assertNotNull(paymentId, "paymentId must not be null");
        assertFalse(paymentId.isBlank(), "paymentId must not be blank");
        assertTrue(paymentId.startsWith("pay_"), "paymentId must start with 'pay_', got: " + paymentId);
    }
}
