package com.prp.web.dto;

import com.prp.domain.AlertStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FraudAlertDto(String id, String paymentId, String customerId, String customerName, BigDecimal amount,
                            String currency, double anomalyScore, List<String> signals, AlertStatus status,
                            Instant detectedAt, String resolvedBy) {}
