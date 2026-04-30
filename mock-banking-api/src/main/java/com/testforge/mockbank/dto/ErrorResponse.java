package com.testforge.mockbank.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@Schema(description = "Error response envelope")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error code", example = "VALIDATION_ERROR")
    private String code;

    @Schema(description = "Human-readable error message", example = "Request validation failed")
    private String message;

    @Schema(description = "Field-level validation errors")
    private List<FieldError> errors;

    @Schema(description = "Request timestamp")
    private Instant timestamp;

    @Data
    @Builder
    @Schema(description = "Field-level validation error")
    public static class FieldError {
        @Schema(description = "Field name", example = "amount")
        private String field;
        @Schema(description = "Validation message", example = "must be greater than 0")
        private String message;
    }
}
