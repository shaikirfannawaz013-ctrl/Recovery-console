package com.prp.web.dto;

import com.prp.domain.RiskBand;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerRowDto(String id, String name, String email, String city, int riskScore, RiskBand riskBand,
                             Instant memberSince, int totalPayments, long failedPayments, Double recoveryRate,
                             BigDecimal openBalance) {}
