package com.prp.web.dto;

import com.prp.domain.*;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentSummaryDto(
        String id, String customerId, String customerName, String description,
        BigDecimal amount, String currency, PaymentMethod method, String gateway,
        FailureReason failureReason, FailureCategory failureCategory, String gatewayMessage,
        PaymentStatus status, double recoveryProbability, int riskScore, NextAction nextAction,
        Instant failedAt, Instant recoveredAt) {}
