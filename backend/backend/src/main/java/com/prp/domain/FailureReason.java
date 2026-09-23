package com.prp.domain;

import static com.prp.domain.FailureCategory.*;
import static com.prp.domain.NextAction.*;

/** Normalised failure reasons. Each maps to a category and a default recovery action. */
public enum FailureReason {
    INSUFFICIENT_FUNDS(SOFT_DECLINE, RETRY_AFTER_PAYDAY),
    NETWORK_TIMEOUT(TECHNICAL, RETRY_SMART),
    BANK_DOWNTIME(TECHNICAL, SWITCH_GATEWAY),
    DO_NOT_HONOR(SOFT_DECLINE, NOTIFY_CUSTOMER),
    LIMIT_EXCEEDED(SOFT_DECLINE, RETRY_SMART),
    CARD_EXPIRED(HARD_DECLINE, REQUEST_CARD_UPDATE),
    INVALID_CVV(HARD_DECLINE, REQUEST_CARD_UPDATE),
    SUSPECTED_FRAUD(FRAUD, MANUAL_REVIEW),
    UNKNOWN(SOFT_DECLINE, NOTIFY_CUSTOMER);

    private final FailureCategory category;
    private final NextAction defaultAction;

    FailureReason(FailureCategory category, NextAction defaultAction) {
        this.category = category;
        this.defaultAction = defaultAction;
    }

    public FailureCategory category() { return category; }
    public NextAction defaultAction() { return defaultAction; }
}
