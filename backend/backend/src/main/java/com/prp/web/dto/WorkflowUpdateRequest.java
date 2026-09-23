package com.prp.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Partial update: null fields are left unchanged. */
public record WorkflowUpdateRequest(
        Boolean enabled,
        @Min(0) @Max(10) Integer maxAttempts,
        @Min(0) @Max(168) Integer backoffHours,
        @DecimalMin("0.0") @DecimalMax("1.0") Double minRecoveryProbability) {}
