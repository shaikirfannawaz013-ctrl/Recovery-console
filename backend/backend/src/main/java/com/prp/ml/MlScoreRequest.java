package com.prp.ml;

/** Features sent to the Python ML service. Field names match its pydantic model. */
public record MlScoreRequest(
        String reasonCode,
        String category,
        double amount,
        String method,
        int hourOfDay,
        int daysSinceSalary,
        int customerFailedCount,
        int customerRecoveredCount,
        int tenureDays,
        int customerRiskScore,
        int velocity10m,
        boolean newDevice,
        boolean ipCityMismatch) {}
