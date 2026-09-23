package com.prp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor
public class Customer {
    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false)
    private String name;

    private String email;
    private String city;

    /** 0-100, from the ML service, smoothed over the customer's payment history. */
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private RiskBand riskBand = RiskBand.LOW;

    /** Day of month the customer's salary usually arrives; drives payday retries. */
    private int salaryDay = 1;

    private Instant memberSince;
    private int totalPayments;

    public void updateRisk(int newScore) {
        this.riskScore = Math.max(0, Math.min(100, newScore));
        this.riskBand = RiskBand.of(this.riskScore);
    }
}
