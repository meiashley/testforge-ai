package com.testforge.mockbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testforge.mockbank.dto.CreatePaymentRequest;
import com.testforge.mockbank.dto.RefundRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createPayment_returns201WithCompletedStatus() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setMerchantId("merchant-001");
        req.setCustomerId("customer-abc");
        req.setAmount(new BigDecimal("128.50"));
        req.setCurrency("CNY");
        req.setDescription("Order #ORD-001");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(128.50));
    }

    @Test
    void getPayment_returnsPayment() throws Exception {
        // create first
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setMerchantId("merchant-001");
        req.setCustomerId("customer-xyz");
        req.setAmount(new BigDecimal("50.00"));
        req.setCurrency("USD");

        MvcResult createResult = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getPayment_returns404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/payments/pay_nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void refundPayment_fullRefund_returnsRefundedStatus() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setMerchantId("merchant-002");
        req.setCustomerId("customer-def");
        req.setAmount(new BigDecimal("200.00"));
        req.setCurrency("CNY");

        MvcResult createResult = mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        RefundRequest refund = new RefundRequest();
        refund.setReason("Customer cancellation");

        mockMvc.perform(post("/api/payments/{id}/refund", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refund)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundReason").value("Customer cancellation"));
    }

    @Test
    void createPayment_invalidCurrency_returns400() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setMerchantId("m");
        req.setCustomerId("c");
        req.setAmount(new BigDecimal("10.00"));
        req.setCurrency("usd"); // lowercase — should fail pattern validation

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
