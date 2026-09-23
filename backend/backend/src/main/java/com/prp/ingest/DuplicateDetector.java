package com.prp.ingest;

import com.prp.config.AppProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Flags a transaction as a duplicate when the same customer pays the same
 * amount with the same instrument (and order, if known) inside a short window.
 * Uses Redis SET NX with a TTL, so it works across backend instances.
 */
@Component
public class DuplicateDetector {

    public record Match(String originalId, long secondsApart, double similarity, List<String> matchedOn) {}

    private final StringRedisTemplate redis;
    private final AppProperties props;

    public DuplicateDetector(StringRedisTemplate redis, AppProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * Records this transaction; if an earlier one with the same fingerprint is
     * still inside the window, returns it as a match.
     */
    public Optional<Match> check(TransactionEvent e, String ourId) {
        String raw = e.customerId() + "|" + e.amount().stripTrailingZeros().toPlainString() + "|"
                + e.instrumentFingerprint() + "|" + (e.orderRef() == null ? "" : e.orderRef());
        String key = "dup:" + DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        long now = e.occurredAt() == null ? Instant.now().toEpochMilli() : e.occurredAt().toEpochMilli();

        Boolean first = redis.opsForValue().setIfAbsent(key, ourId + "|" + now, props.duplicates().window());
        if (Boolean.TRUE.equals(first)) return Optional.empty();

        String existing = redis.opsForValue().get(key);
        if (existing == null) return Optional.empty();
        String[] parts = existing.split("\\|");
        if (parts[0].equals(ourId)) return Optional.empty(); // Kafka redelivery of the same transaction
        long secondsApart = Math.max(0, (now - Long.parseLong(parts[1])) / 1000);

        List<String> matchedOn = new ArrayList<>(List.of("Same customer", "Same amount"));
        matchedOn.add(switch (e.method()) {
            case UPI -> "Same UPI ID";
            case CARD -> "Same card";
            case NETBANKING -> "Same bank account";
            case WALLET -> "Same wallet";
        });
        double similarity = 0.9;
        if (e.orderRef() != null && !e.orderRef().isBlank()) {
            matchedOn.add("Same order reference");
            similarity = 1.0;
        }
        return Optional.of(new Match(parts[0], secondsApart, similarity, matchedOn));
    }
}
