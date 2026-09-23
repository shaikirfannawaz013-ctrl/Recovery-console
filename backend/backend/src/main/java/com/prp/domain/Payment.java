package com.prp.domain;

import com.prp.common.Factor;
import com.prp.common.FactorListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A failed payment and everything the platform decided about it. */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_status", columnList = "status"),
        @Index(name = "idx_payment_failed_at", columnList = "failedAt"),
        @Index(name = "idx_payment_customer", columnList = "customer_id")
})
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id
    @Column(length = 20)
    private String id;

    /** ID from the payment gateway; unique so Kafka redeliveries are ignored. */
    @Column(nullable = false, unique = true, length = 64)
    private String gatewayTxnId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING) @Column(length = 20)
    private PaymentMethod method;

    private String gateway;
    private String instrumentFingerprint;
    private String orderRef;

    @Enumerated(EnumType.STRING) @Column(length = 30)
    private FailureReason failureReason;

    @Enumerated(EnumType.STRING) @Column(length = 20)
    private FailureCategory failureCategory;

    private String responseCode;
    private String gatewayMessage;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymentStatus status;

    private double recoveryProbability;
    private int riskScore;
    private double anomalyScore;
    private String modelVersion;

    @Convert(converter = FactorListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<Factor> topFactors = new ArrayList<>();

    @Enumerated(EnumType.STRING) @Column(length = 30)
    private NextAction nextAction;

    @Column(nullable = false)
    private Instant failedAt;
    private Instant recoveredAt;
    private Instant lastNotifiedAt;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attempt ASC")
    private List<PaymentAttempt> attempts = new ArrayList<>();

    /** Optimistic locking: a scheduled retry and a manual retry can't both win. */
    @Version
    private Long version;

    public PaymentAttempt addAttempt(String gatewayName, AttemptOutcome outcome, String code) {
        PaymentAttempt a = new PaymentAttempt(this, attempts.size() + 1, Instant.now(), gatewayName, outcome, code);
        attempts.add(a);
        return a;
    }

    public boolean isOpen() {
        return PaymentStatus.OPEN.contains(status);
    }
}
