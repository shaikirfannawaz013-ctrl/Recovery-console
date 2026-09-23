package com.prp.repository;

import com.prp.domain.Payment;
import com.prp.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String>, JpaSpecificationExecutor<Payment> {

    boolean existsByGatewayTxnId(String gatewayTxnId);

    @EntityGraph(attributePaths = "customer")
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "attempts"})
    Optional<Payment> findWithDetailsById(String id);

    long countByCustomerIdAndStatus(String customerId, PaymentStatus status);

    long countByCustomerId(String customerId);

    /** status, count, sum(amount) for the window. */
    @Query("select p.status, count(p), sum(p.amount) from Payment p where p.failedAt >= :since group by p.status")
    List<Object[]> totalsByStatus(@Param("since") Instant since);

    @Query("select p.failedAt, p.amount, p.status from Payment p where p.failedAt >= :since")
    List<Object[]> amountsSince(@Param("since") Instant since);

    @Query("""
            select p.failureReason, p.failureCategory, count(p),
                   sum(case when p.status = :recovered then 1 else 0 end)
            from Payment p where p.failedAt >= :since
            group by p.failureReason, p.failureCategory
            order by count(p) desc""")
    List<Object[]> reasonBreakdown(@Param("since") Instant since, @Param("recovered") PaymentStatus recovered);

    /** customerId, failed count, recovered count, open balance. */
    @Query("""
            select p.customer.id, count(p),
                   sum(case when p.status = :recovered then 1 else 0 end),
                   sum(case when p.status in :open then p.amount end)
            from Payment p where p.customer.id in :ids
            group by p.customer.id""")
    List<Object[]> statsForCustomers(@Param("ids") Collection<String> ids,
                                     @Param("recovered") PaymentStatus recovered,
                                     @Param("open") Collection<PaymentStatus> open);
}
