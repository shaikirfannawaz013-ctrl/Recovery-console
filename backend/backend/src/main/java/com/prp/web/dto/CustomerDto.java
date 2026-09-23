package com.prp.web.dto;

import com.prp.domain.RiskBand;

import java.time.Instant;

public record CustomerDto(String id, String name, String email, String city, int riskScore, RiskBand riskBand,
                          Instant memberSince, int totalPayments) {}
