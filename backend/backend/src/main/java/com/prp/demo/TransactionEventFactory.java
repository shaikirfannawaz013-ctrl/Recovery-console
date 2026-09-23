package com.prp.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prp.common.Ids;
import com.prp.config.AppProperties;
import com.prp.domain.Customer;
import com.prp.domain.PaymentMethod;
import com.prp.ingest.TransactionEvent;
import com.prp.repository.CustomerRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Builds realistic gateway transactions and publishes them to Kafka, like a real gateway webhook bridge would. */
@Component
public class TransactionEventFactory {

    private record Code(String code, String message, int weight) {}

    private static final List<Code> CODES = List.of(
            new Code("51", "Insufficient balance in account", 26),
            new Code("68", "Gateway did not respond within 30s", 16),
            new Code("91", "Issuer bank unavailable", 8),
            new Code("05", "Issuer declined: do not honor", 14),
            new Code("61", "Daily transaction limit exceeded", 10),
            new Code("54", "Card expired", 10),
            new Code("82", "Incorrect CVV entered", 8),
            new Code("59", "Blocked by risk engine", 4));
    private static final int TOTAL_WEIGHT = CODES.stream().mapToInt(Code::weight).sum();
    private static final List<String> PLANS = List.of("Pro plan renewal", "Annual membership", "Order checkout",
            "Insurance premium", "EMI instalment", "Team plan renewal", "Wallet top-up");
    private static final List<Integer> AMOUNTS = List.of(199, 499, 999, 1499, 2999, 4999, 7999, 12499, 24999);
    private static final List<String> GATEWAYS = List.of("Razorpay", "PayU", "Cashfree", "Stripe");
    private static final List<String> CITIES = List.of("Kozhikode", "Bengaluru", "Hyderabad", "Chennai", "Mumbai", "Delhi");

    private final CustomerRepository customers;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;

    public TransactionEventFactory(CustomerRepository customers, KafkaTemplate<String, String> kafka,
                                   ObjectMapper mapper, AppProperties props) {
        this.customers = customers;
        this.kafka = kafka;
        this.mapper = mapper;
        this.topic = props.topics().transactions();
    }

    public int publishRandomFailures(int count) {
        List<Customer> all = customers.findAll();
        if (all.isEmpty()) return 0;
        var rnd = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Customer c = all.get(rnd.nextInt(all.size()));
            Code code = pickCode();
            PaymentMethod method = PaymentMethod.values()[rnd.nextInt(PaymentMethod.values().length)];
            String device = "dev-" + c.getId() + (rnd.nextDouble() < 0.08 ? "-" + rnd.nextInt(1000) : "");
            String city = rnd.nextDouble() < 0.1 ? CITIES.get(rnd.nextInt(CITIES.size())) : c.getCity();
            send(event(c, false, code.code(), code.message(), method, device, city, "ORD-" + Ids.next("X").substring(2), Instant.now()));
        }
        return count;
    }

    /** The same successful charge arriving twice a few seconds apart. */
    public int publishDuplicatePair() {
        List<Customer> all = customers.findAll();
        if (all.isEmpty()) return 0;
        var rnd = ThreadLocalRandom.current();
        Customer c = all.get(rnd.nextInt(all.size()));
        PaymentMethod method = rnd.nextBoolean() ? PaymentMethod.UPI : PaymentMethod.CARD;
        String order = "ORD-" + Ids.next("X").substring(2);
        Instant now = Instant.now();
        TransactionEvent first = event(c, true, "00", "Approved", method, "dev-" + c.getId(), c.getCity(), order, now);
        TransactionEvent second = event(c, true, "00", "Approved", method, "dev-" + c.getId(), c.getCity(), order,
                now.plusSeconds(rnd.nextInt(3, 45)));
        second = new TransactionEvent(second.gatewayTxnId(), c.getId(), c.getName(), c.getEmail(), first.description(),
                first.amount(), "INR", method, first.gateway(), true, "00", "Approved",
                first.instrumentFingerprint(), order, second.deviceId(), second.ipCity(), second.occurredAt());
        send(first);
        send(second);
        return 2;
    }

    /** Several quick failures from a new device in another city: should trip the fraud checks. */
    public int publishFraudBurst() {
        List<Customer> all = customers.findAll();
        if (all.isEmpty()) return 0;
        var rnd = ThreadLocalRandom.current();
        Customer c = all.get(rnd.nextInt(all.size()));
        String device = "dev-burst-" + rnd.nextInt(100_000);
        String city = CITIES.stream().filter(x -> !x.equalsIgnoreCase(c.getCity())).findFirst().orElse("Delhi");
        for (int i = 0; i < 7; i++) {
            send(event(c, false, "05", "Issuer declined: do not honor", PaymentMethod.CARD, device, city,
                    "ORD-" + Ids.next("X").substring(2), Instant.now()));
        }
        return 7;
    }

    private TransactionEvent event(Customer c, boolean success, String code, String message, PaymentMethod method,
                                   String device, String city, String orderRef, Instant at) {
        var rnd = ThreadLocalRandom.current();
        int amount = AMOUNTS.get(rnd.nextInt(AMOUNTS.size())) + (rnd.nextDouble() < 0.3 ? rnd.nextInt(1, 99) : 0);
        return new TransactionEvent(
                "gw_" + Ids.next("T").substring(2).toLowerCase(), c.getId(), c.getName(), c.getEmail(),
                PLANS.get(rnd.nextInt(PLANS.size())), BigDecimal.valueOf(amount), "INR", method,
                GATEWAYS.get(rnd.nextInt(GATEWAYS.size())), success, code, message,
                "fp-" + c.getId() + "-" + method.name().toLowerCase(), orderRef, device, city, at);
    }

    private Code pickCode() {
        int r = ThreadLocalRandom.current().nextInt(TOTAL_WEIGHT);
        for (Code c : CODES) {
            r -= c.weight();
            if (r < 0) return c;
        }
        return CODES.get(0);
    }

    private void send(TransactionEvent e) {
        try {
            kafka.send(topic, e.customerId(), mapper.writeValueAsString(e));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not publish demo transaction", ex);
        }
    }
}
