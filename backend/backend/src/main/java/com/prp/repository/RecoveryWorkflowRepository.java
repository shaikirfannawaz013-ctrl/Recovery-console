package com.prp.repository;

import com.prp.domain.FailureCategory;
import com.prp.domain.RecoveryWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecoveryWorkflowRepository extends JpaRepository<RecoveryWorkflow, String> {
    Optional<RecoveryWorkflow> findByCategory(FailureCategory category);
}
