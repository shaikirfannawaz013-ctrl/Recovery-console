package com.prp.web;

import com.prp.demo.TransactionEventFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin-only: push synthetic gateway transactions into Kafka to watch the pipeline work. */
@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private final TransactionEventFactory factory;

    public SimulationController(TransactionEventFactory factory) {
        this.factory = factory;
    }

    @PostMapping("/transactions")
    public Map<String, Object> transactions(@RequestParam(defaultValue = "10") int count,
                                            @RequestParam(defaultValue = "normal") String scenario) {
        int n = Math.max(1, Math.min(count, 200));
        int sent = switch (scenario) {
            case "duplicate" -> factory.publishDuplicatePair();
            case "fraud-burst" -> factory.publishFraudBurst();
            default -> factory.publishRandomFailures(n);
        };
        return Map.of("published", sent, "scenario", scenario);
    }
}
