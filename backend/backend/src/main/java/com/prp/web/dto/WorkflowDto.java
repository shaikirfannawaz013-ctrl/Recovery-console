package com.prp.web.dto;

import com.prp.domain.FailureCategory;

import java.util.List;

public record WorkflowDto(String id, String name, FailureCategory category, boolean enabled, int maxAttempts,
                          int backoffHours, double minRecoveryProbability, List<String> steps) {}
