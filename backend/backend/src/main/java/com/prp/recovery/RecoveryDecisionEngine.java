package com.prp.recovery;

import com.prp.common.Ids;
import com.prp.domain.*;
import com.prp.events.EventPublisher;
import com.prp.events.EventType;
import com.prp.repository.FraudAlertRepository;
import com.prp.repository.RecoveryWorkflowRepository;
import com.prp.repository.RetryScheduleRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Decides what happens to a failed payment: hold for fraud review, schedule a
 * smart retry, ask for a new card, or contact the customer. Must be called
 * inside a transaction.
 */
@Component
public class RecoveryDecisionEngine {

    private final RecoveryWorkflowRepository workflows;
    private final RetryScheduleRepository retries;
    private final FraudAlertRepository alerts;
    private final RetryPlanner planner;
    private final NotificationService notifications;
    private final EventPublisher events;

    public RecoveryDecisionEngine(RecoveryWorkflowRepository workflows, RetryScheduleRepository retries,
                                  FraudAlertRepository alerts, RetryPlanner planner,
                                  NotificationService notifications, EventPublisher events) {
        this.workflows = workflows;
        this.retries = retries;
        this.alerts = alerts;
        this.planner = planner;
        this.notifications = notifications;
        this.events = events;
    }

    public void decide(Payment p, boolean fraudFlagged, List<String> signals) {
        if (fraudFlagged || p.getFailureReason().defaultAction() == NextAction.MANUAL_REVIEW) {
            holdForReview(p, signals);
        } else {
            route(p);
        }
    }

    /** An analyst marked the payment safe: send it back into normal recovery. */
    public void releaseFromReview(Payment p) {
        route(p);
    }

    private void route(Payment p) {
        RecoveryWorkflow wf = workflows.findByCategory(p.getFailureCategory()).orElse(null);
        NextAction action = p.getFailureReason().defaultAction();
        if (action == NextAction.MANUAL_REVIEW) action = NextAction.RETRY_SMART; // cleared by a reviewer

        if (wf == null || !wf.isEnabled()) {
            // automation off for this category: leave it for an analyst
            p.setStatus(PaymentStatus.FAILED);
            p.setNextAction(action);
            return;
        }
        if (action == NextAction.REQUEST_CARD_UPDATE) {
            // retrying an expired card or wrong CVV cannot succeed
            p.setStatus(PaymentStatus.FAILED);
            p.setNextAction(NextAction.REQUEST_CARD_UPDATE);
            notifications.sendCardUpdateLink(p);
            return;
        }
        int nextAttempt = p.getAttempts().size() + 1;
        if (nextAttempt > wf.getMaxAttempts() || p.getRecoveryProbability() < wf.getMinRecoveryProbability()) {
            p.setStatus(PaymentStatus.FAILED);
            p.setNextAction(NextAction.NOTIFY_CUSTOMER);
            notifications.sendReminder(p);
            return;
        }
        if (action == NextAction.NOTIFY_CUSTOMER) notifications.sendReminder(p);
        scheduleRetry(p, action, nextAttempt, wf);
    }

    /** After a declined retry: schedule the next one if the rule allows, otherwise stop. */
    public void afterDecline(Payment p) {
        RecoveryWorkflow wf = workflows.findByCategory(p.getFailureCategory()).orElse(null);
        int done = p.getAttempts().size();
        NextAction action = p.getNextAction() != null && p.getNextAction().isRetry()
                ? p.getNextAction() : p.getFailureReason().defaultAction();

        if (wf != null && wf.isEnabled() && action.isRetry() && done < wf.getMaxAttempts()) {
            scheduleRetry(p, action, done + 1, wf);
        } else {
            p.setStatus(PaymentStatus.FAILED);
            p.setNextAction(p.getFailureCategory() == FailureCategory.HARD_DECLINE
                    ? NextAction.REQUEST_CARD_UPDATE : NextAction.NOTIFY_CUSTOMER);
            notifications.sendReminder(p);
        }
    }

    public void holdForReview(Payment p, List<String> signals) {
        p.setStatus(PaymentStatus.UNDER_REVIEW);
        p.setNextAction(NextAction.MANUAL_REVIEW);
        cancelPendingRetries(p);
        if (!alerts.existsByPaymentIdAndStatus(p.getId(), AlertStatus.OPEN)) {
            FraudAlert a = new FraudAlert();
            a.setId(Ids.next("FRD"));
            a.setPayment(p);
            a.setAnomalyScore(p.getAnomalyScore());
            a.setSignals(signals.isEmpty() ? List.of("Flagged by the anomaly model") : signals);
            a.setDetectedAt(Instant.now());
            alerts.save(a);
        }
        events.publish(EventType.FRAUD_FLAGGED, p);
    }

    public void cancelPendingRetries(Payment p) {
        retries.findByPaymentIdAndStatus(p.getId(), RetryStatus.PENDING)
                .forEach(r -> r.setStatus(RetryStatus.CANCELLED));
    }

    private void scheduleRetry(Payment p, NextAction action, int attempt, RecoveryWorkflow wf) {
        retries.save(planner.plan(p, action, attempt, wf));
        p.setStatus(PaymentStatus.RETRY_SCHEDULED);
        p.setNextAction(action);
        events.publish(EventType.RETRY_SCHEDULED, p);
    }
}
