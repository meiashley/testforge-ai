package com.testforge.mockbank.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class Payment {

    private String id;
    private String merchantId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String description;
    private String refundReason;
    private Instant createdAt;
    private Instant updatedAt;

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED, PARTIALLY_REFUNDED
    }
}
