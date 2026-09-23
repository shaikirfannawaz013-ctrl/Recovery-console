package com.prp.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.prp.common.Factor;
import com.prp.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentDetailDto(
        String id, String customerId, String customerName, String description,
        BigDecimal amount, String currency, PaymentMethod method, String gateway,
        FailureReason failureReason, FailureCategory failureCategory, String gatewayMessage, String responseCode,
        PaymentStatus status, double recoveryProbability, int riskScore, double anomalyScore, NextAction nextAction,
        Instant failedAt, Instant recoveredAt, Instant lastNotifiedAt,
        List<Attempt> attempts, Model model, CustomerDto customer, Boolean lastAttemptSucceeded) {

    public record Attempt(int attempt, Instant at, String gateway, AttemptOutcome outcome, String responseCode) {}

    public record Model(String version, List<Factor> topFactors) {}

    public PaymentDetailDto withLastAttempt(boolean succeeded) {
        return new PaymentDetailDto(id, customerId, customerName, description, amount, currency, method, gateway,
                failureReason, failureCategory, gatewayMessage, responseCode, status, recoveryProbability, riskScore,
                anomalyScore, nextAction, failedAt, recoveredAt, lastNotifiedAt, attempts, model, customer, succeeded);
    }
}
