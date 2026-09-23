package com.prp.recovery;

import com.prp.common.Ids;
import com.prp.domain.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Smart retry scheduling: decides when (and through which gateway) the next
 * attempt should run, based on the failure and the customer.
 */
@Component
public class RetryPlanner {

    /** Local hours with the best issuer approval rates. */
    private static final List<Integer> BEST_HOURS = List.of(10, 15);

    private final ZoneId zone;
    private final GatewayClient gateways;

    public RetryPlanner(ZoneId businessZone, GatewayClient gateways) {
        this.zone = businessZone;
        this.gateways = gateways;
    }

    public RetrySchedule plan(Payment p, NextAction action, int attempt, RecoveryWorkflow wf) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant at;
        String note;
        String gateway = p.getGateway();

        if (attempt > 1) {
            // exponential backoff after the first retry: backoff, 2x, 4x ...
            long hours = Math.max(1, wf.getBackoffHours()) * (1L << Math.min(attempt - 2, 4));
            at = now.plusHours(hours).toInstant();
            note = "Waiting " + hours + " h after the previous decline";
            if (action == NextAction.SWITCH_GATEWAY) gateway = gateways.backupFor(p.getGateway());
        } else if (p.getFailureCategory() == FailureCategory.TECHNICAL && action == NextAction.RETRY_SMART) {
            at = now.plusMinutes(15).toInstant();
            note = "Quick retry after a temporary gateway error";
        } else {
            switch (action) {
                case RETRY_SMART -> {
                    at = nextBestHour(now).toInstant();
                    note = "Off-peak window with the highest issuer approval rate";
                }
                case RETRY_AFTER_PAYDAY -> {
                    ZonedDateTime payday = nextPayday(now, p.getCustomer().getSalaryDay());
                    if (Duration.between(now, payday).toDays() <= 10) {
                        at = payday.toInstant();
                        note = "Timed for 1 day after the expected salary credit";
                    } else {
                        at = now.plusHours(Math.max(1, wf.getBackoffHours())).toInstant();
                        note = "Next payday is too far away, using standard backoff";
                    }
                }
                case SWITCH_GATEWAY -> {
                    gateway = gateways.backupFor(p.getGateway());
                    at = now.plusMinutes(10).toInstant();
                    note = "Routed to the backup gateway (" + gateway + ")";
                }
                case NOTIFY_CUSTOMER -> {
                    at = now.plusHours(Math.max(6, wf.getBackoffHours())).toInstant();
                    note = "After the customer reminder is delivered";
                }
                default -> {
                    at = now.plusHours(Math.max(1, wf.getBackoffHours())).toInstant();
                    note = "Standard backoff";
                }
            }
        }

        RetrySchedule r = new RetrySchedule();
        r.setId(Ids.next("RTY"));
        r.setPayment(p);
        r.setStrategy(action);
        r.setTimingNote(note);
        r.setGateway(gateway);
        r.setAttempt(attempt);
        r.setMaxAttempts(wf.getMaxAttempts());
        r.setScheduledAt(at);
        return r;
    }

    ZonedDateTime nextBestHour(ZonedDateTime now) {
        ZonedDateTime earliest = now.plusMinutes(30);
        for (int dayOffset = 0; dayOffset < 2; dayOffset++) {
            for (int hour : BEST_HOURS) {
                ZonedDateTime c = now.plusDays(dayOffset).withHour(hour).withMinute(30).withSecond(0).withNano(0);
                if (c.isAfter(earliest)) return c;
            }
        }
        return earliest;
    }

    ZonedDateTime nextPayday(ZonedDateTime now, int salaryDay) {
        int day = Math.min(Math.max(salaryDay, 1) + 1, 28);
        ZonedDateTime c = now.withDayOfMonth(day).withHour(10).withMinute(0).withSecond(0).withNano(0);
        return c.isAfter(now.plusMinutes(30)) ? c : c.plusMonths(1);
    }
}
