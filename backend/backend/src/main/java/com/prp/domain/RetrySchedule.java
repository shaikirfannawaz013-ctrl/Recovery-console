package com.prp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "retry_schedules", indexes = @Index(name = "idx_retry_due", columnList = "status, scheduledAt"))
@Getter @Setter @NoArgsConstructor
public class RetrySchedule {
    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Payment payment;

    @Enumerated(EnumType.STRING) @Column(length = 30)
    private NextAction strategy;

    private String timingNote;
    private String gateway;
    private int attempt;
    private int maxAttempts;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private RetryStatus status = RetryStatus.PENDING;

    @Version
    private Long version;
}
