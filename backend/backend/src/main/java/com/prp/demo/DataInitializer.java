package com.prp.demo;

import com.prp.common.Factor;
import com.prp.common.Ids;
import com.prp.config.AppProperties;
import com.prp.domain.*;
import com.prp.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static com.prp.domain.FailureCategory.*;

/**
 * First-run setup: login users and default recovery rules always; 14 days of
 * sample history when app.demo.seed-history=true. Does nothing if data exists.
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String[] FIRST = {"Aarav", "Diya", "Vihaan", "Ananya", "Arjun", "Fathima", "Rohan", "Meera",
            "Kabir", "Sneha", "Aditya", "Nisha", "Irfan", "Lakshmi", "Rahul", "Zara", "Karthik", "Priya", "Sameer", "Divya"};
    private static final String[] LAST = {"Sharma", "Nair", "Reddy", "Khan", "Iyer", "Menon", "Patel", "Das", "Rao",
            "Pillai", "Gupta", "Varma", "Joseph", "Singh", "Hegde"};
    private static final String[] CITIES = {"Kozhikode", "Bengaluru", "Hyderabad", "Chennai", "Mumbai", "Pune", "Delhi", "Tirupati"};
    private static final String[] GATEWAYS = {"Razorpay", "PayU", "Cashfree", "Stripe"};
    private static final String[] PLANS = {"Pro plan renewal", "Annual membership", "Order checkout", "Insurance premium",
            "EMI instalment", "Team plan renewal", "Wallet top-up"};
    private static final int[] AMOUNTS = {199, 499, 999, 1499, 2999, 4999, 7999, 12499, 24999};
    private static final Map<FailureReason, Double> BASE = Map.of(
            FailureReason.INSUFFICIENT_FUNDS, 0.62, FailureReason.NETWORK_TIMEOUT, 0.9,
            FailureReason.BANK_DOWNTIME, 0.84, FailureReason.DO_NOT_HONOR, 0.42,
            FailureReason.LIMIT_EXCEEDED, 0.56, FailureReason.CARD_EXPIRED, 0.24,
            FailureReason.INVALID_CVV, 0.2, FailureReason.SUSPECTED_FRAUD, 0.04);

    private final AppUserRepository users;
    private final RecoveryWorkflowRepository workflows;
    private final CustomerRepository customers;
    private final PaymentRepository payments;
    private final RetryScheduleRepository retries;
    private final FraudAlertRepository alerts;
    private final DuplicateCaseRepository duplicates;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final Random rnd = new Random(20260923);

    public DataInitializer(AppUserRepository users, RecoveryWorkflowRepository workflows, CustomerRepository customers,
                           PaymentRepository payments, RetryScheduleRepository retries, FraudAlertRepository alerts,
                           DuplicateCaseRepository duplicates, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.workflows = workflows;
        this.customers = customers;
        this.payments = payments;
        this.retries = retries;
        this.alerts = alerts;
        this.duplicates = duplicates;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            users.save(new AppUser("admin", encoder.encode("admin123"), "Ops Admin", Role.ADMIN));
            users.save(new AppUser("analyst", encoder.encode("analyst123"), "Recovery Analyst", Role.ANALYST));
            log.info("Created users admin/admin123 and analyst/analyst123 — change these passwords");
        }
        if (workflows.count() == 0) {
            workflows.saveAll(List.of(
                    new RecoveryWorkflow("WF-SOFT", "Soft declines", SOFT_DECLINE, 4, 24, 0.3, List.of(
                            "Retry at the best predicted time", "Send payment reminder",
                            "Retry after salary credit", "Escalate to collections")),
                    new RecoveryWorkflow("WF-TECH", "Technical failures", TECHNICAL, 3, 1, 0.1, List.of(
                            "Retry in 15 minutes", "Switch to backup gateway", "Retry on backup gateway")),
                    new RecoveryWorkflow("WF-HARD", "Hard declines", HARD_DECLINE, 1, 72, 0.15, List.of(
                            "Send card update link", "Send reminder after 3 days", "Write off after 7 days")),
                    new RecoveryWorkflow("WF-FRAUD", "Suspected fraud", FRAUD, 0, 0, 0, List.of(
                            "Hold the payment", "Send to fraud review", "Block the customer if confirmed"))));
        }
        if (props.demo().seedHistory() && customers.count() == 0) {
            seedHistory();
        }
    }

    private void seedHistory() {
        Instant now = Instant.now();
        List<Customer> cs = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Customer c = new Customer();
            c.setId("CUS-" + (1001 + i));
            String name = FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)];
            c.setName(name);
            c.setEmail(name.toLowerCase().replace(' ', '.') + "@mail.com");
            c.setCity(CITIES[rnd.nextInt(CITIES.length)]);
            c.updateRisk((int) Math.round(Math.pow(rnd.nextDouble(), 1.6) * 100));
            c.setSalaryDay(1 + rnd.nextInt(28));
            c.setMemberSince(now.minus(Duration.ofDays(40 + rnd.nextInt(860))));
            c.setTotalPayments(6 + rnd.nextInt(70));
            cs.add(c);
        }
        cs = customers.saveAll(cs);

        FailureReason[] reasons = BASE.keySet().toArray(FailureReason[]::new);
        Arrays.sort(reasons);
        List<Payment> ps = new ArrayList<>();
        for (int i = 0; i < 220; i++) {
            Customer c = cs.get(rnd.nextInt(cs.size()));
            FailureReason reason = reasons[rnd.nextInt(reasons.length)];
            Instant failedAt = now.minus(Duration.ofMinutes(rnd.nextInt(14 * 24 * 60)));
            double prob = clamp(BASE.get(reason) + (rnd.nextDouble() - 0.5) * 0.25 - c.getRiskScore() / 400.0, 0.01, 0.99);
            double ageDays = Duration.between(failedAt, now).toHours() / 24.0;

            Payment p = new Payment();
            p.setId(Ids.next("PAY"));
            p.setGatewayTxnId("seed_" + i + "_" + Ids.next("S"));
            p.setCustomer(c);
            p.setDescription(PLANS[rnd.nextInt(PLANS.length)]);
            p.setAmount(BigDecimal.valueOf(AMOUNTS[rnd.nextInt(AMOUNTS.length)]));
            p.setMethod(PaymentMethod.values()[rnd.nextInt(4)]);
            p.setGateway(GATEWAYS[rnd.nextInt(GATEWAYS.length)]);
            p.setFailureReason(reason);
            p.setFailureCategory(reason.category());
            p.setGatewayMessage(reason.name().replace('_', ' ').toLowerCase());
            p.setResponseCode("51");
            p.setFailedAt(failedAt);
            p.setRecoveryProbability(round(prob));
            p.setRiskScore((int) Math.round(clamp(c.getRiskScore() / 100.0 + (rnd.nextDouble() - 0.5) * 0.2
                    + (reason.category() == FRAUD ? 0.35 : 0), 0, 1) * 100));
            p.setAnomalyScore(reason.category() == FRAUD ? 0.8 : round(rnd.nextDouble() * 0.4));
            p.setModelVersion("seed-data");
            p.setTopFactors(List.of(new Factor("Failure reason", round(BASE.get(reason) - 0.5)),
                    new Factor("Customer risk score", round(-c.getRiskScore() / 250.0))));

            PaymentStatus status;
            if (reason.category() == FRAUD) status = rnd.nextBoolean() ? PaymentStatus.UNDER_REVIEW : PaymentStatus.BLOCKED;
            else if (ageDays > 1 && rnd.nextDouble() < prob * 0.85) status = PaymentStatus.RECOVERED;
            else if (reason.category() == HARD_DECLINE && ageDays > 7) status = PaymentStatus.WRITTEN_OFF;
            else if (reason.category() == HARD_DECLINE) status = PaymentStatus.FAILED;
            else status = rnd.nextDouble() > 0.35 ? PaymentStatus.RETRY_SCHEDULED : PaymentStatus.FAILED;
            p.setStatus(status);

            int attempts = status == PaymentStatus.FAILED || status == PaymentStatus.UNDER_REVIEW ? 0 : 1 + rnd.nextInt(2);
            for (int a = 1; a <= attempts; a++) {
                boolean last = a == attempts;
                AttemptOutcome outcome = last && status == PaymentStatus.RECOVERED ? AttemptOutcome.SUCCESS : AttemptOutcome.DECLINED;
                Instant at = failedAt.plus(Duration.ofHours((long) a * (2 + rnd.nextInt(20))));
                if (at.isAfter(now)) at = now.minusSeconds(60);
                p.getAttempts().add(new PaymentAttempt(p, a, at, p.getGateway(), outcome,
                        outcome == AttemptOutcome.SUCCESS ? "00" : "51"));
                if (outcome == AttemptOutcome.SUCCESS) p.setRecoveredAt(at);
            }
            p.setNextAction(switch (status) {
                case RECOVERED, WRITTEN_OFF, BLOCKED -> null;
                case UNDER_REVIEW -> NextAction.MANUAL_REVIEW;
                default -> reason.defaultAction();
            });
            ps.add(p);
        }
        ps = payments.saveAll(ps);

        for (Payment p : ps) {
            if (p.getStatus() == PaymentStatus.RETRY_SCHEDULED) {
                RetrySchedule r = new RetrySchedule();
                r.setId(Ids.next("RTY"));
                r.setPayment(p);
                r.setStrategy(p.getNextAction());
                r.setTimingNote("Off-peak window with the highest issuer approval rate");
                r.setGateway(p.getGateway());
                r.setAttempt(p.getAttempts().size() + 1);
                r.setMaxAttempts(4);
                r.setScheduledAt(now.plus(Duration.ofMinutes(5 + rnd.nextInt(96 * 60))));
                retries.save(r);
            }
            if (p.getFailureCategory() == FRAUD) {
                FraudAlert a = new FraudAlert();
                a.setId(Ids.next("FRD"));
                a.setPayment(p);
                a.setAnomalyScore(round(0.7 + rnd.nextDouble() * 0.29));
                a.setSignals(List.of("Gateway risk engine blocked the payment", "Device seen for the first time"));
                a.setStatus(p.getStatus() == PaymentStatus.UNDER_REVIEW ? AlertStatus.OPEN : AlertStatus.CONFIRMED);
                a.setDetectedAt(p.getFailedAt());
                alerts.save(a);
            }
        }
        for (int i = 0; i < 6; i++) {
            Customer c = cs.get(rnd.nextInt(cs.size()));
            DuplicateCase d = new DuplicateCase();
            d.setId(Ids.next("DUP"));
            d.setCustomer(c);
            d.setAmount(BigDecimal.valueOf(AMOUNTS[rnd.nextInt(AMOUNTS.length)]));
            d.setCurrency("INR");
            d.setOriginalId("gw_" + Ids.next("T").substring(2).toLowerCase());
            d.setDuplicateId("gw_" + Ids.next("T").substring(2).toLowerCase());
            d.setSecondsApart(3 + rnd.nextInt(90));
            d.setSimilarity(1.0);
            d.setMatchedOn(List.of("Same customer", "Same amount", "Same card", "Same order reference"));
            d.setDetectedAt(now.minus(Duration.ofHours(rnd.nextInt(150))));
            duplicates.save(d);
        }
        log.info("Seeded {} customers and {} payments of sample history", cs.size(), ps.size());
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double round(double v) { return Math.round(v * 100) / 100.0; }
}
