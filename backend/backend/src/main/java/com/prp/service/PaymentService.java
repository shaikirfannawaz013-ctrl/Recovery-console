package com.prp.service;

import com.prp.common.BadRequestException;
import com.prp.common.NotFoundException;
import com.prp.domain.*;
import com.prp.recovery.NotificationService;
import com.prp.recovery.RecoveryDecisionEngine;
import com.prp.recovery.RetryService;
import com.prp.repository.PaymentRepository;
import com.prp.web.dto.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PaymentService {

    private static final Set<String> SORTABLE = Set.of("failedAt", "recoveryProbability", "amount");

    private final PaymentRepository payments;
    private final RetryService retryService;
    private final RecoveryDecisionEngine engine;
    private final NotificationService notifications;

    public PaymentService(PaymentRepository payments, RetryService retryService,
                          RecoveryDecisionEngine engine, NotificationService notifications) {
        this.payments = payments;
        this.retryService = retryService;
        this.engine = engine;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentSummaryDto> search(PaymentStatus status, FailureCategory category, String q,
                                                  String sort, int page, int size) {
        String sortField = SORTABLE.contains(sort) ? sort : "failedAt";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, sortField).and(Sort.by("id")));
        return PageResponse.of(payments.findAll(filter(status, category, q), pageable), Mappers::summary);
    }

    @Transactional(readOnly = true)
    public PaymentDetailDto get(String id) {
        return Mappers.detail(load(id));
    }

    /** Manual "Retry now" from the dashboard. */
    @Transactional
    public PaymentDetailDto retryNow(String id) {
        Payment p = load(id);
        if (p.getStatus() != PaymentStatus.FAILED && p.getStatus() != PaymentStatus.RETRY_SCHEDULED) {
            throw new BadRequestException("Only failed or scheduled payments can be retried.");
        }
        engine.cancelPendingRetries(p);
        boolean ok = retryService.attempt(p, p.getGateway());
        return Mappers.detail(p).withLastAttempt(ok);
    }

    @Transactional
    public PaymentDetailDto runAction(String id, PaymentAction action, String user) {
        Payment p = load(id);
        if (!p.isOpen()) throw new BadRequestException("This payment is already closed.");
        switch (action) {
            case REQUEST_CARD_UPDATE -> notifications.sendCardUpdateLink(p);
            case NOTIFY_CUSTOMER -> notifications.sendReminder(p);
            case ESCALATE -> engine.holdForReview(p, List.of("Sent to review by " + user));
            case WRITE_OFF -> {
                engine.cancelPendingRetries(p);
                p.setStatus(PaymentStatus.WRITTEN_OFF);
                p.setNextAction(null);
            }
        }
        return Mappers.detail(p);
    }

    private Payment load(String id) {
        return payments.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Payment " + id + " was not found."));
    }

    static Specification<Payment> filter(PaymentStatus status, FailureCategory category, String q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (category != null) ps.add(cb.equal(root.get("failureCategory"), category));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                var customer = root.join("customer", JoinType.INNER);
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("id")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(customer.get("name")), like),
                        cb.like(cb.lower(customer.get("id")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }
}
