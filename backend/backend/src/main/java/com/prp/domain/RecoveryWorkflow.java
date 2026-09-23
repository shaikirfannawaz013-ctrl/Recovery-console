package com.prp.domain;

import com.prp.common.StringListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Admin-editable recovery rule for one failure category. */
@Entity
@Table(name = "recovery_workflows")
@Getter @Setter @NoArgsConstructor
public class RecoveryWorkflow {
    @Id
    @Column(length = 20)
    private String id;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private FailureCategory category;

    private boolean enabled = true;
    private int maxAttempts;
    private int backoffHours;
    /** Below this predicted chance we stop retrying and contact the customer instead. */
    private double minRecoveryProbability;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> steps = new ArrayList<>();

    public RecoveryWorkflow(String id, String name, FailureCategory category, int maxAttempts,
                            int backoffHours, double minRecoveryProbability, List<String> steps) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.maxAttempts = maxAttempts;
        this.backoffHours = backoffHours;
        this.minRecoveryProbability = minRecoveryProbability;
        this.steps = new ArrayList<>(steps);
    }
}
