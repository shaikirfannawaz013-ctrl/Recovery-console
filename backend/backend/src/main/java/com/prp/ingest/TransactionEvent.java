package com.prp.ingest;

import com.prp.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A transaction as reported by a payment gateway on the Kafka topic
 * (default name: payments.transactions). success=false means it failed.
 */
public record TransactionEvent(
        String gatewayTxnId,
        String customerId,
        String customerName,
        String customerEmail,
        String description,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        String gateway,
        boolean success,
        String responseCode,
        String gatewayMessage,
        String instrumentFingerprint,
        String orderRef,
        String deviceId,
        String ipCity,
        Instant occurredAt) {}
