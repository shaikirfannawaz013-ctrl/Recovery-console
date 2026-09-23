package com.prp.domain;

import com.prp.common.StringListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Two transactions that look like the same payment made twice. */
@Entity
@Table(name = "duplicate_cases")
@Getter @Setter @NoArgsConstructor
public class DuplicateCase {
    @Id
    @Column(length = 20)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;
    private String currency;

    /** Our payment ID if the first transaction failed, otherwise the gateway transaction ID. */
    private String originalId;
    private String duplicateId;
    private long secondsApart;
    private double similarity;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> matchedOn = new ArrayList<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15)
    private DuplicateStatus status = DuplicateStatus.OPEN;

    private Instant detectedAt;
    private String resolvedBy;
}
