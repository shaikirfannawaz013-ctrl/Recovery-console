package com.prp.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PaymentStatus {
    FAILED, RETRY_SCHEDULED, UNDER_REVIEW, RECOVERED, WRITTEN_OFF, BLOCKED;

    /** Money that is still being worked on. */
    public static final Set<PaymentStatus> OPEN = EnumSet.of(FAILED, RETRY_SCHEDULED, UNDER_REVIEW);
    /** Money that will not come back. */
    public static final Set<PaymentStatus> LOST = EnumSet.of(WRITTEN_OFF, BLOCKED);
}
