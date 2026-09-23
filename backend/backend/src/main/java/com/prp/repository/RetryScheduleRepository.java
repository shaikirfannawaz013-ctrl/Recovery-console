package com.prp.repository;

import com.prp.domain.RetrySchedule;
import com.prp.domain.RetryStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RetryScheduleRepository extends JpaRepository<RetrySchedule, String> {

    @EntityGraph(attributePaths = {"payment", "payment.customer"})
    List<RetrySchedule> findByStatusOrderByScheduledAtAsc(RetryStatus status);

    List<RetrySchedule> findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(RetryStatus status, Instant now, Pageable page);

    List<RetrySchedule> findByPaymentIdAndStatus(String paymentId, RetryStatus status);

    long countByStatusAndScheduledAtBefore(RetryStatus status, Instant before);
}
