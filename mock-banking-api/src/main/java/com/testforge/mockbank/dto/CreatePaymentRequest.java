package com.testforge.mockbank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request body for creating a new payment")
public class CreatePaymentRequest {

    @NotBlank
    @Size(max = 64)
    @Schema(description = "Merchant identifier", example = "merchant-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String merchantId;

    @NotBlank
    @Size(max = 64)
    @Schema(description = "Customer identifier", example = "customer-abc", requiredMode = Schema.RequiredMode.REQUIRED)
    private String customerId;

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "999999.99")
    @Digits(integer = 6, fraction = 2)
    @Schema(description = "Payment amount", example = "128.50", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO 4217 currency code")
    @Schema(description = "ISO 4217 currency code", example = "CNY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String currency;

    @Size(max = 255)
    @Schema(description = "Optional payment description", example = "Order #ORD-20260430-001")
    private String description;
}
