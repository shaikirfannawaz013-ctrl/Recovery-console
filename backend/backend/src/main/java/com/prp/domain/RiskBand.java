package com.prp.domain;

public enum RiskBand {
    LOW, MEDIUM, HIGH;

    public static RiskBand of(int score) {
        return score > 66 ? HIGH : score > 33 ? MEDIUM : LOW;
    }
}
