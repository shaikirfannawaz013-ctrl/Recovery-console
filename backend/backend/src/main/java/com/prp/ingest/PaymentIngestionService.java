package com.prp.ingest;

import com.prp.common.Ids;
import com.prp.config.AppProperties;
import com.prp.domain.*;
import com.prp.events.EventPublisher;
import com.prp.events.EventType;
import com.prp.ml.MlClient;
import com.prp.ml.MlScoreRequest;
import com.prp.ml.MlScoreResponse;
import com.prp.recovery.RecoveryDecisionEngine;
import com.prp.repository.CustomerRepository;
import com.prp.repository.DuplicateCaseRepository;
import com.prp.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * The core pipeline for every transaction from Kafka:
 * idempotency → duplicate check → classify → behaviour signals → ML scoring
 * → fraud check → recovery decision.
 */
@Service
public class PaymentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIngestionService.class);

    private final PaymentRepository payments;
    private final CustomerRepository customers;
    private final DuplicateCaseRepository duplicateCases;
    private final FailureClassifier classifier;
    private final DuplicateDetector duplicateDetector;
    private final FraudSignals fraudSignals;
    private final MlClient ml;
    private final RecoveryDecisionEngine engine;
    private final EventPublisher events;
    private final AppProperties props;
    private final ZoneId zone;

    public PaymentIngestionService(PaymentRepository payments, CustomerRepository customers,
                                   DuplicateCaseRepository duplicateCases, FailureClassifier classifier,
                                   DuplicateDetector duplicateDetector, FraudSignals fraudSignals, MlClient ml,
                                   RecoveryDecisionEngine engine, EventPublisher events, AppProperties props,
                                   ZoneId businessZone) {
        this.payments = payments;
        this.customers = customers;
        this.duplicateCases = duplicateCases;
        this.classifier = classifier;
        this.duplicateDetector = duplicateDetector;
        this.fraudSignals = fraudSignals;
        this.ml = ml;
        this.engine = engine;
        this.events = events;
        this.props = props;
        this.zone = businessZone;
    }

    @Transactional
    public void ingest(TransactionEvent e) {
        if (e.gatewayTxnId() == null || e.customerId() == null || e.amount() == null || e.method() == null) {
            throw new IllegalArgumentException("Transaction event is missing required fields");
        }
        if (payments.existsByGatewayTxnId(e.gatewayTxnId())) {
            log.debug("Already processed {}", e.gatewayTxnId());
            return;
        }

        Customer customer = customers.findById(e.customerId()).orElseGet(() -> newCustomer(e));
        customer.setTotalPayments(customer.getTotalPayments() + 1);
        Instant occurredAt = e.occurredAt() == null ? Instant.now() : e.occurredAt();
        String paymentId = Ids.next("PAY");

        // 1. duplicate detection
        Optional<DuplicateDetector.Match> dup = duplicateDetector.check(e, e.success() ? e.gatewayTxnId() : paymentId);
        if (dup.isPresent()) {
            if (e.success()) {
                recordDuplicateCharge(customer, e, dup.get());
            } else {
                log.info("Failed transaction {} repeats {}; handled by the original", e.gatewayTxnId(), dup.get().originalId());
            }
            return;
        }
        if (e.success()) return; // unique successful charge: nothing to recover

        // 2. classification
        FailureReason reason = classifier.classify(e.responseCode(), e.gatewayMessage());
        Payment p = new Payment();
        p.setId(paymentId);
        p.setGatewayTxnId(e.gatewayTxnId());
        p.setCustomer(customer);
        p.setDescription(e.description());
        p.setAmount(e.amount());
        p.setCurrency(e.currency() == null ? "INR" : e.currency());
        p.setMethod(e.method());
        p.setGateway(e.gateway());
        p.setInstrumentFingerprint(e.instrumentFingerprint());
        p.setOrderRef(e.orderRef());
        p.setResponseCode(e.responseCode());
        p.setGatewayMessage(e.gatewayMessage());
        p.setFailureReason(reason);
        p.setFailureCategory(reason.category());
        p.setFailedAt(occurredAt);
        p.setStatus(PaymentStatus.FAILED);

        // 3. behaviour signals + ML scoring
        FraudSignals.Snapshot signals = fraudSignals.observe(customer, e, props.fraud().velocityLimit());
        MlScoreResponse score = ml.score(features(p, customer, signals, occurredAt));
        p.setRecoveryProbability(score.recoveryProbability());
        p.setRiskScore(score.riskScore());
        p.setAnomalyScore(score.anomalyScore());
        p.setTopFactors(score.topFactors() == null ? List.of() : score.topFactors());
        p.setModelVersion(score.modelVersion());
        customer.updateRisk((int) Math.round(customer.getRiskScore() * 0.8 + score.riskScore() * 0.2));

        payments.save(p);
        events.publish(EventType.PAYMENT_FAILED, p);

        // 4. fraud check, then recovery decision
        boolean flagged = reason.category() == FailureCategory.FRAUD
                || score.anomalyScore() >= props.fraud().anomalyThreshold()
                || signals.velocity10m() > props.fraud().velocityLimit();
        LinkedHashSet<String> reasons = new LinkedHashSet<>(signals.signals());
        if (score.signals() != null) reasons.addAll(score.signals());
        if (reason == FailureReason.SUSPECTED_FRAUD) reasons.add("Gateway risk engine blocked the payment");

        engine.decide(p, flagged, new ArrayList<>(reasons));
    }

    private MlScoreRequest features(Payment p, Customer c, FraudSignals.Snapshot s, Instant occurredAt) {
        ZonedDateTime local = occurredAt.atZone(zone);
        int daysSinceSalary = Math.floorMod(local.getDayOfMonth() - c.getSalaryDay(), 30);
        int tenureDays = c.getMemberSince() == null ? 0 : (int) Duration.between(c.getMemberSince(), occurredAt).toDays();
        return new MlScoreRequest(
                p.getFailureReason().name(), p.getFailureCategory().name(), p.getAmount().doubleValue(),
                p.getMethod().name(), local.getHour(), daysSinceSalary,
                (int) payments.countByCustomerId(c.getId()),
                (int) payments.countByCustomerIdAndStatus(c.getId(), PaymentStatus.RECOVERED),
                Math.max(0, tenureDays), c.getRiskScore(),
                s.velocity10m(), s.newDevice(), s.ipCityMismatch());
    }

    private void recordDuplicateCharge(Customer c, TransactionEvent e, DuplicateDetector.Match m) {
        DuplicateCase d = new DuplicateCase();
        d.setId(Ids.next("DUP"));
        d.setCustomer(c);
        d.setAmount(e.amount());
        d.setCurrency(e.currency() == null ? "INR" : e.currency());
        d.setOriginalId(m.originalId());
        d.setDuplicateId(e.gatewayTxnId());
        d.setSecondsApart(m.secondsApart());
        d.setSimilarity(m.similarity());
        d.setMatchedOn(m.matchedOn());
        d.setDetectedAt(Instant.now());
        duplicateCases.save(d);
        events.publishDuplicate(e.gatewayTxnId(), c.getName(), e.amount(), d.getCurrency(), e.gateway());
    }

    private Customer newCustomer(TransactionEvent e) {
        Customer c = new Customer();
        c.setId(e.customerId());
        c.setName(e.customerName() == null ? e.customerId() : e.customerName());
        c.setEmail(e.customerEmail());
        c.setCity(e.ipCity());
        c.setMemberSince(Instant.now());
        c.updateRisk(30);
        return customers.save(c);
    }
}
