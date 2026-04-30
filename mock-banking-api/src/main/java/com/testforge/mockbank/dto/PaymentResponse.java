package com.testforge.mockbank.dto;

import com.testforge.mockbank.model.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@Schema(description = "Payment resource representation")
public class PaymentResponse {

    @Schema(description = "Unique payment identifier", example = "pay_a1b2c3d4e5f6")
    private String id;

    @Schema(description = "Merchant identifier", example = "merchant-001")
    private String merchantId;

    @Schema(description = "Customer identifier", example = "customer-abc")
    private String customerId;

    @Schema(description = "Payment amount", example = "128.50")
    private BigDecimal amount;

    @Schema(description = "ISO 4217 currency code", example = "CNY")
    private String currency;

    @Schema(description = "Current payment status")
    private Payment.PaymentStatus status;

    @Schema(description = "Payment description", example = "Order #ORD-20260430-001")
    private String description;

    @Schema(description = "Refund reason, present only when status is REFUNDED or PARTIALLY_REFUNDED")
    private String refundReason;

    @Schema(description = "ISO 8601 creation timestamp")
    private Instant createdAt;

    @Schema(description = "ISO 8601 last-update timestamp")
    private Instant updatedAt;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .merchantId(p.getMerchantId())
                .customerId(p.getCustomerId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .description(p.getDescription())
                .refundReason(p.getRefundReason())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
