package com.prp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payment_attempts")
@Getter @Setter @NoArgsConstructor
public class PaymentAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Payment payment;

    private int attempt;
    private Instant at;
    private String gateway;

    @Enumerated(EnumType.STRING) @Column(length = 10)
    private AttemptOutcome outcome;

    private String responseCode;

    public PaymentAttempt(Payment payment, int attempt, Instant at, String gateway, AttemptOutcome outcome, String responseCode) {
        this.payment = payment;
        this.attempt = attempt;
        this.at = at;
        this.gateway = gateway;
        this.outcome = outcome;
        this.responseCode = responseCode;
    }
}
