package com.testforge.mockbank.exception;

import com.testforge.mockbank.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(PaymentNotFoundException ex) {
        return ErrorResponse.builder()
                .status(404)
                .code("PAYMENT_NOT_FOUND")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }

    @ExceptionHandler(PaymentNotRefundableException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleNotRefundable(PaymentNotRefundableException ex) {
        return ErrorResponse.builder()
                .status(422)
                .code("PAYMENT_NOT_REFUNDABLE")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        List<ErrorResponse.FieldError> fieldErrors = result.getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();

        return ErrorResponse.builder()
                .status(400)
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .errors(fieldErrors)
                .timestamp(Instant.now())
                .build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return ErrorResponse.builder()
                .status(400)
                .code("BAD_REQUEST")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();
    }
}
