package com.testforge.mockbank.exception;

import com.testforge.mockbank.model.Payment;

public class PaymentNotRefundableException extends RuntimeException {
    public PaymentNotRefundableException(String id, Payment.PaymentStatus status) {
        super("Payment " + id + " cannot be refunded in status: " + status);
    }
}
