package com.testforge.mockbank.service;

import com.testforge.mockbank.dto.CreatePaymentRequest;
import com.testforge.mockbank.dto.RefundRequest;
import com.testforge.mockbank.exception.PaymentNotFoundException;
import com.testforge.mockbank.exception.PaymentNotRefundableException;
import com.testforge.mockbank.model.Payment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {

    private final Map<String, Payment> store = new ConcurrentHashMap<>();

    public Payment createPayment(CreatePaymentRequest req) {
        String id = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();
        Payment payment = Payment.builder()
                .id(id)
                .merchantId(req.getMerchantId())
                .customerId(req.getCustomerId())
                .amount(req.getAmount())
                .currency(req.getCurrency())
                .description(req.getDescription())
                .status(Payment.PaymentStatus.COMPLETED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        store.put(id, payment);
        return payment;
    }

    public Payment getPayment(String id) {
        Payment payment = store.get(id);
        if (payment == null) {
            throw new PaymentNotFoundException(id);
        }
        return payment;
    }

    public Payment refundPayment(String id, RefundRequest req) {
        Payment payment = getPayment(id);

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new PaymentNotRefundableException(id, payment.getStatus());
        }

        BigDecimal refundAmount = req.getAmount() != null ? req.getAmount() : payment.getAmount();

        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount " + refundAmount + " exceeds original payment amount " + payment.getAmount());
        }

        Payment.PaymentStatus newStatus = refundAmount.compareTo(payment.getAmount()) == 0
                ? Payment.PaymentStatus.REFUNDED
                : Payment.PaymentStatus.PARTIALLY_REFUNDED;

        Payment updated = Payment.builder()
                .id(payment.getId())
                .merchantId(payment.getMerchantId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .description(payment.getDescription())
                .status(newStatus)
                .refundReason(req.getReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        store.put(id, updated);
        return updated;
    }
}
