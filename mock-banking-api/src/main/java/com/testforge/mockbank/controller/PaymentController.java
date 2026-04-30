package com.testforge.mockbank.controller;

import com.testforge.mockbank.dto.*;
import com.testforge.mockbank.model.Payment;
import com.testforge.mockbank.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment lifecycle operations")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a payment",
        description = "Initiates a new payment. Returns COMPLETED immediately (mock behaviour)."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment created",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = paymentService.createPayment(request);
        return PaymentResponse.from(payment);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get a payment",
        description = "Retrieves a payment by its unique identifier."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment found",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PaymentResponse getPayment(
            @Parameter(description = "Payment ID", example = "pay_a1b2c3d4e5f6")
            @PathVariable String id) {
        return PaymentResponse.from(paymentService.getPayment(id));
    }

    @PostMapping("/{id}/refund")
    @Operation(
        summary = "Refund a payment",
        description = "Issues a full or partial refund for a COMPLETED payment."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Refund processed",
            content = @Content(schema = @Schema(implementation = PaymentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid refund amount",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Payment not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Payment is not refundable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PaymentResponse refundPayment(
            @Parameter(description = "Payment ID", example = "pay_a1b2c3d4e5f6")
            @PathVariable String id,
            @Valid @RequestBody RefundRequest request) {
        return PaymentResponse.from(paymentService.refundPayment(id, request));
    }
}
