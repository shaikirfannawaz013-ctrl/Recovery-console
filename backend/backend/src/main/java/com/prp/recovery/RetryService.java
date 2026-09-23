package com.prp.recovery;

import com.prp.domain.*;
import com.prp.events.EventPublisher;
import com.prp.events.EventType;
import com.prp.repository.RetryScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RetryService {

    private final RetryScheduleRepository retries;
    private final GatewayClient gateway;
    private final RecoveryDecisionEngine engine;
    private final EventPublisher events;

    public RetryService(RetryScheduleRepository retries, GatewayClient gateway,
                        RecoveryDecisionEngine engine, EventPublisher events) {
        this.retries = retries;
        this.gateway = gateway;
        this.engine = engine;
        this.events = events;
    }

    /** Runs one scheduled retry. Called by the scheduler under a Redis lock. */
    @Transactional
    public void executeScheduled(String retryId) {
        RetrySchedule r = retries.findById(retryId).orElse(null);
        if (r == null || r.getStatus() != RetryStatus.PENDING) return;
        r.setStatus(RetryStatus.EXECUTED);
        Payment p = r.getPayment();
        if (p.getStatus() != PaymentStatus.RETRY_SCHEDULED) return;
        attempt(p, r.getGateway());
    }

    /** Charges the payment once and applies the outcome. Returns true if recovered. */
    public boolean attempt(Payment p, String gatewayName) {
        String via = gatewayName == null ? p.getGateway() : gatewayName;
        GatewayClient.ChargeResult result = gateway.charge(p, via);
        p.addAttempt(via, result.success() ? AttemptOutcome.SUCCESS : AttemptOutcome.DECLINED, result.responseCode());

        if (result.success()) {
            p.setStatus(PaymentStatus.RECOVERED);
            p.setRecoveredAt(Instant.now());
            p.setNextAction(null);
            Customer c = p.getCustomer();
            c.updateRisk(c.getRiskScore() - 3);
            events.publish(EventType.RETRY_SUCCEEDED, p);
            return true;
        }
        events.publish(EventType.RETRY_FAILED, p);
        engine.afterDecline(p);
        return false;
    }
}
