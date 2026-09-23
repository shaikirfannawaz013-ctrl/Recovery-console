package com.prp.recovery;

import com.prp.domain.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for real gateway SDK calls. A retry succeeds with roughly the
 * probability the ML model predicted, a little higher on a different gateway.
 * Replace with real Razorpay / PayU clients for production.
 */
@Component
public class SimulatedGatewayClient implements GatewayClient {

    private static final Logger log = LoggerFactory.getLogger(SimulatedGatewayClient.class);
    private static final List<String> GATEWAYS = List.of("Razorpay", "PayU", "Cashfree", "Stripe");

    @Override
    public ChargeResult charge(Payment p, String gateway) {
        double chance = p.getRecoveryProbability() + (gateway != null && !gateway.equals(p.getGateway()) ? 0.1 : 0);
        boolean ok = ThreadLocalRandom.current().nextDouble() < Math.min(chance, 0.97);
        log.info("Charging {} via {} -> {}", p.getId(), gateway, ok ? "approved" : "declined");
        return new ChargeResult(ok, ok ? "00" : p.getResponseCode());
    }

    @Override
    public void refund(String transactionId, BigDecimal amount) {
        log.info("Refund of {} requested for {}", amount, transactionId);
    }

    @Override
    public String backupFor(String gateway) {
        int i = GATEWAYS.indexOf(gateway);
        return GATEWAYS.get((i + 1) % GATEWAYS.size());
    }
}
