package com.prp.events;

import java.math.BigDecimal;
import java.time.Instant;

/** What the dashboard receives on /topic/transactions. */
public record RecoveryEvent(
        String eventId,
        EventType type,
        String paymentId,
        String customerName,
        BigDecimal amount,
        String currency,
        String reason,
        String gateway,
        Instant timestamp) {}
