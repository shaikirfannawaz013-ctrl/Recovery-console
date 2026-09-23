package com.prp.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Fraud: CONFIRM | DISMISS. Duplicates: REFUND | KEEP. */
public record DecisionRequest(@NotBlank String decision) {}
