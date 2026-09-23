package com.prp.domain;

public enum NextAction {
    RETRY_SMART, RETRY_AFTER_PAYDAY, SWITCH_GATEWAY, NOTIFY_CUSTOMER, REQUEST_CARD_UPDATE, MANUAL_REVIEW;

    public boolean isRetry() {
        return this == RETRY_SMART || this == RETRY_AFTER_PAYDAY || this == SWITCH_GATEWAY || this == NOTIFY_CUSTOMER;
    }
}
