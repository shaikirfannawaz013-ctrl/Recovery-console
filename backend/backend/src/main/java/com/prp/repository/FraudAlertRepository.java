package com.prp.repository;

import com.prp.domain.AlertStatus;
import com.prp.domain.FraudAlert;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, String> {

    @EntityGraph(attributePaths = {"payment", "payment.customer"})
    List<FraudAlert> findByStatus(AlertStatus status, Sort sort);

    @EntityGraph(attributePaths = {"payment", "payment.customer"})
    List<FraudAlert> findAllBy(Sort sort);

    boolean existsByPaymentIdAndStatus(String paymentId, AlertStatus status);

    long countByStatus(AlertStatus status);
}
