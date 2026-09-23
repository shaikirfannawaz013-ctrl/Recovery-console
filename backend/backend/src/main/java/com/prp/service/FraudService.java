package com.prp.service;

import com.prp.common.BadRequestException;
import com.prp.common.NotFoundException;
import com.prp.domain.*;
import com.prp.recovery.RecoveryDecisionEngine;
import com.prp.repository.FraudAlertRepository;
import com.prp.web.dto.FraudAlertDto;
import com.prp.web.dto.Mappers;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class FraudService {

    private static final Sort BY_SCORE = Sort.by(Sort.Direction.DESC, "anomalyScore");
    private final FraudAlertRepository alerts;
    private final RecoveryDecisionEngine engine;

    public FraudService(FraudAlertRepository alerts, RecoveryDecisionEngine engine) {
        this.alerts = alerts;
        this.engine = engine;
    }

    @Transactional(readOnly = true)
    public List<FraudAlertDto> list(AlertStatus status) {
        var list = status == null ? alerts.findAllBy(BY_SCORE) : alerts.findByStatus(status, BY_SCORE);
        return list.stream().map(Mappers::fraud).toList();
    }

    @Transactional
    public FraudAlertDto resolve(String id, String decision, String user) {
        FraudAlert a = alerts.findById(id).orElseThrow(() -> new NotFoundException("Alert not found."));
        if (a.getStatus() != AlertStatus.OPEN) throw new BadRequestException("This alert was already resolved.");
        Payment p = a.getPayment();

        switch (decision.toUpperCase()) {
            case "CONFIRM" -> {
                a.setStatus(AlertStatus.CONFIRMED);
                engine.cancelPendingRetries(p);
                p.setStatus(PaymentStatus.BLOCKED);
                p.setNextAction(null);
                p.getCustomer().updateRisk(Math.max(p.getCustomer().getRiskScore(), 85));
            }
            case "DISMISS" -> {
                a.setStatus(AlertStatus.DISMISSED);
                if (p.getStatus() == PaymentStatus.UNDER_REVIEW) engine.releaseFromReview(p);
            }
            default -> throw new BadRequestException("Decision must be CONFIRM or DISMISS.");
        }
        a.setResolvedBy(user);
        a.setResolvedAt(Instant.now());
        return Mappers.fraud(a);
    }
}
