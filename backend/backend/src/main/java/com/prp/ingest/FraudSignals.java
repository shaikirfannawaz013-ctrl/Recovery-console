package com.prp.ingest;

import com.prp.domain.Customer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Real-time behavioural signals kept in Redis: velocity and new devices. */
@Component
public class FraudSignals {

    public record Snapshot(int velocity10m, boolean newDevice, boolean ipCityMismatch, List<String> signals) {}

    private final StringRedisTemplate redis;

    public FraudSignals(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Snapshot observe(Customer customer, TransactionEvent e, int velocityLimit) {
        String vKey = "velocity:" + customer.getId();
        Long count = redis.opsForValue().increment(vKey);
        if (count != null && count == 1) redis.expire(vKey, Duration.ofMinutes(10));
        int velocity = count == null ? 1 : count.intValue();

        boolean newDevice = false;
        if (e.deviceId() != null) {
            String dKey = "devices:" + customer.getId();
            Long known = redis.opsForSet().size(dKey);
            Long added = redis.opsForSet().add(dKey, e.deviceId());
            newDevice = known != null && known > 0 && added != null && added > 0;
        }
        boolean ipMismatch = e.ipCity() != null && customer.getCity() != null
                && !e.ipCity().equalsIgnoreCase(customer.getCity());

        List<String> signals = new ArrayList<>();
        if (velocity > velocityLimit) signals.add(velocity + " payment attempts in 10 minutes");
        if (newDevice) signals.add("Device seen for the first time");
        if (ipMismatch) signals.add("IP location (" + e.ipCity() + ") differs from billing city");
        return new Snapshot(velocity, newDevice, ipMismatch, signals);
    }
}
