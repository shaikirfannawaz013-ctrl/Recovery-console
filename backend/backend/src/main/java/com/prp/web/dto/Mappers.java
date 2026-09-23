package com.prp.web.dto;

import com.prp.domain.*;

import java.util.List;

public final class Mappers {
    private Mappers() {}

    public static UserDto user(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole());
    }

    public static CustomerDto customer(Customer c) {
        return new CustomerDto(c.getId(), c.getName(), c.getEmail(), c.getCity(), c.getRiskScore(), c.getRiskBand(),
                c.getMemberSince(), c.getTotalPayments());
    }

    public static PaymentSummaryDto summary(Payment p) {
        Customer c = p.getCustomer();
        return new PaymentSummaryDto(p.getId(), c.getId(), c.getName(), p.getDescription(), p.getAmount(),
                p.getCurrency(), p.getMethod(), p.getGateway(), p.getFailureReason(), p.getFailureCategory(),
                p.getGatewayMessage(), p.getStatus(), p.getRecoveryProbability(), p.getRiskScore(),
                p.getNextAction(), p.getFailedAt(), p.getRecoveredAt());
    }

    public static PaymentDetailDto detail(Payment p) {
        Customer c = p.getCustomer();
        List<PaymentDetailDto.Attempt> attempts = p.getAttempts().stream()
                .map(a -> new PaymentDetailDto.Attempt(a.getAttempt(), a.getAt(), a.getGateway(), a.getOutcome(), a.getResponseCode()))
                .toList();
        return new PaymentDetailDto(p.getId(), c.getId(), c.getName(), p.getDescription(), p.getAmount(),
                p.getCurrency(), p.getMethod(), p.getGateway(), p.getFailureReason(), p.getFailureCategory(),
                p.getGatewayMessage(), p.getResponseCode(), p.getStatus(), p.getRecoveryProbability(),
                p.getRiskScore(), p.getAnomalyScore(), p.getNextAction(), p.getFailedAt(), p.getRecoveredAt(),
                p.getLastNotifiedAt(), attempts,
                new PaymentDetailDto.Model(p.getModelVersion(), List.copyOf(p.getTopFactors())),
                customer(c), null);
    }

    public static RetryDto retry(RetrySchedule r) {
        Payment p = r.getPayment();
        return new RetryDto(r.getId(), p.getId(), p.getCustomer().getName(), p.getAmount(), p.getCurrency(),
                p.getFailureReason(), r.getStrategy(), r.getTimingNote(), r.getGateway(), r.getAttempt(),
                r.getMaxAttempts(), p.getRecoveryProbability(), r.getScheduledAt());
    }

    public static FraudAlertDto fraud(FraudAlert a) {
        Payment p = a.getPayment();
        return new FraudAlertDto(a.getId(), p.getId(), p.getCustomer().getId(), p.getCustomer().getName(),
                p.getAmount(), p.getCurrency(), a.getAnomalyScore(), List.copyOf(a.getSignals()), a.getStatus(),
                a.getDetectedAt(), a.getResolvedBy());
    }

    public static DuplicateDto duplicate(DuplicateCase d) {
        return new DuplicateDto(d.getId(), d.getCustomer().getName(), d.getAmount(), d.getCurrency(),
                d.getOriginalId(), d.getDuplicateId(), d.getSecondsApart(), d.getSimilarity(),
                List.copyOf(d.getMatchedOn()), d.getStatus(), d.getDetectedAt());
    }

    public static WorkflowDto workflow(RecoveryWorkflow w) {
        return new WorkflowDto(w.getId(), w.getName(), w.getCategory(), w.isEnabled(), w.getMaxAttempts(),
                w.getBackoffHours(), w.getMinRecoveryProbability(), List.copyOf(w.getSteps()));
    }
}
