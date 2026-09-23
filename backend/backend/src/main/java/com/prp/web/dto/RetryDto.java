package com.prp.web.dto;

import com.prp.domain.FailureReason;
import com.prp.domain.NextAction;

import java.math.BigDecimal;
import java.time.Instant;

public record RetryDto(String id, String paymentId, String customerName, BigDecimal amount, String currency,
                       FailureReason failureReason, NextAction strategy, String timingNote, String gateway,
                       int attempt, int maxAttempts, double recoveryProbability, Instant scheduledAt) {}
