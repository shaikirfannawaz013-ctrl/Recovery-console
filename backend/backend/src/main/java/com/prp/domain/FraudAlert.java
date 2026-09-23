package com.prp.domain;

import com.prp.common.StringListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fraud_alerts")
@Getter @Setter @NoArgsConstructor
public class FraudAlert {
    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Payment payment;

    private double anomalyScore;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> signals = new ArrayList<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12)
    private AlertStatus status = AlertStatus.OPEN;

    private Instant detectedAt;
    private String resolvedBy;
    private Instant resolvedAt;
}
