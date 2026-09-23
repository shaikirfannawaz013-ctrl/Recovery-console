package com.prp.demo;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/** Keeps a steady trickle of gateway traffic flowing so the dashboard is alive. Disable with app.demo.traffic=false. */
@Component
@ConditionalOnProperty(name = "app.demo.traffic", havingValue = "true")
public class DemoTrafficGenerator {

    private final TransactionEventFactory factory;

    public DemoTrafficGenerator(TransactionEventFactory factory) {
        this.factory = factory;
    }

    @Scheduled(fixedDelayString = "${app.demo.traffic-interval-ms}", initialDelay = 20_000)
    public void tick() {
        double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.025) factory.publishFraudBurst();
        else if (r < 0.09) factory.publishDuplicatePair();
        else factory.publishRandomFailures(1);
    }
}
