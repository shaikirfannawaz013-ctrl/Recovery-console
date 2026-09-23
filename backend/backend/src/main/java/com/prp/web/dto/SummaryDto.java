package com.prp.web.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record SummaryDto(
        int periodDays,
        BigDecimal failedAmount, BigDecimal recoveredAmount, BigDecimal inProgressAmount, BigDecimal lostAmount,
        long failedCount, long recoveredCount, long inProgressCount, long lostCount,
        double recoveryRate, long openFraudAlerts, long openDuplicates, long retriesNext24h,
        String currency) implements Serializable {}
