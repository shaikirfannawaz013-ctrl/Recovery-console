package com.prp.web.dto;

import com.prp.domain.DuplicateStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DuplicateDto(String id, String customerName, BigDecimal amount, String currency, String originalId,
                           String duplicateId, long secondsApart, double similarity, List<String> matchedOn,
                           DuplicateStatus status, Instant detectedAt) {}
