package com.prp.recovery;

import com.prp.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPlannerTest {

    private final ZoneId ist = ZoneId.of("Asia/Kolkata");
    private final RetryPlanner planner = new RetryPlanner(ist, new SimulatedGatewayClient());
    private final RecoveryWorkflow wf = new RecoveryWorkflow("WF", "Soft", FailureCategory.SOFT_DECLINE, 4, 24, 0.3, List.of());

    private Payment payment(FailureReason reason) {
        Customer c = new Customer();
        c.setSalaryDay(5);
        Payment p = new Payment();
        p.setCustomer(c);
        p.setGateway("Razorpay");
        p.setAmount(BigDecimal.TEN);
        p.setFailureReason(reason);
        p.setFailureCategory(reason.category());
        return p;
    }

    @Test
    void smartRetryLandsOnABestHour() {
        RetrySchedule r = planner.plan(payment(FailureReason.LIMIT_EXCEEDED), NextAction.RETRY_SMART, 1, wf);
        int hour = r.getScheduledAt().atZone(ist).getHour();
        assertThat(hour).isIn(10, 15);
        assertThat(r.getScheduledAt()).isAfter(Instant.now());
    }

    @Test
    void technicalFailureRetriesQuickly() {
        RetrySchedule r = planner.plan(payment(FailureReason.NETWORK_TIMEOUT), NextAction.RETRY_SMART, 1, wf);
        assertThat(Duration.between(Instant.now(), r.getScheduledAt()).toMinutes()).isBetween(14L, 15L);
    }

    @Test
    void switchGatewayUsesBackup() {
        RetrySchedule r = planner.plan(payment(FailureReason.BANK_DOWNTIME), NextAction.SWITCH_GATEWAY, 1, wf);
        assertThat(r.getGateway()).isEqualTo("PayU");
    }

    @Test
    void paydayIsTheDayAfterSalary() {
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 20, 12, 0, 0, 0, ist);
        assertThat(planner.nextPayday(now, 5).getDayOfMonth()).isEqualTo(6);
        assertThat(planner.nextPayday(now, 5).getMonthValue()).isEqualTo(10);
    }

    @Test
    void laterAttemptsBackOffExponentially() {
        RetrySchedule second = planner.plan(payment(FailureReason.LIMIT_EXCEEDED), NextAction.RETRY_SMART, 2, wf);
        RetrySchedule third = planner.plan(payment(FailureReason.LIMIT_EXCEEDED), NextAction.RETRY_SMART, 3, wf);
        long h2 = Duration.between(Instant.now(), second.getScheduledAt()).toHours();
        long h3 = Duration.between(Instant.now(), third.getScheduledAt()).toHours();
        assertThat(h2).isBetween(23L, 24L);
        assertThat(h3).isBetween(47L, 48L);
    }
}
