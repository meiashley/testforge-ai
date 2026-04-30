package com.testforge.mockbank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Request body for initiating a refund")
public class RefundRequest {

    @DecimalMin(value = "0.01")
    @Digits(integer = 6, fraction = 2)
    @Schema(description = "Refund amount; if omitted the full payment amount is refunded", example = "50.00")
    private BigDecimal amount;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Reason for the refund", example = "Customer requested cancellation", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
