package com.prp.service;

import com.prp.domain.*;
import com.prp.repository.DuplicateCaseRepository;
import com.prp.repository.FraudAlertRepository;
import com.prp.repository.PaymentRepository;
import com.prp.repository.RetryScheduleRepository;
import com.prp.web.dto.ReasonCountDto;
import com.prp.web.dto.SummaryDto;
import com.prp.web.dto.TrendPointDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/** Dashboard numbers. Cached in Redis for a short time (see spring.cache in application.yml). */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final PaymentRepository payments;
    private final FraudAlertRepository alerts;
    private final DuplicateCaseRepository duplicates;
    private final RetryScheduleRepository retries;
    private final ZoneId zone;

    public AnalyticsService(PaymentRepository payments, FraudAlertRepository alerts, DuplicateCaseRepository duplicates,
                            RetryScheduleRepository retries, ZoneId businessZone) {
        this.payments = payments;
        this.alerts = alerts;
        this.duplicates = duplicates;
        this.retries = retries;
        this.zone = businessZone;
    }

    @Cacheable(cacheNames = "analytics-summary", key = "#days")
    public SummaryDto summary(int days) {
        Instant since = Instant.now().minus(Duration.ofDays(days));
        Map<PaymentStatus, long[]> counts = new EnumMap<>(PaymentStatus.class);
        Map<PaymentStatus, BigDecimal> sums = new EnumMap<>(PaymentStatus.class);
        for (Object[] row : payments.totalsByStatus(since)) {
            PaymentStatus s = (PaymentStatus) row[0];
            counts.put(s, new long[]{((Number) row[1]).longValue()});
            sums.put(s, toBigDecimal(row[2]));
        }
        BigDecimal recovered = sum(sums, EnumSet.of(PaymentStatus.RECOVERED));
        BigDecimal open = sum(sums, PaymentStatus.OPEN);
        BigDecimal lost = sum(sums, PaymentStatus.LOST);
        long recoveredCount = count(counts, EnumSet.of(PaymentStatus.RECOVERED));
        long total = count(counts, EnumSet.allOf(PaymentStatus.class));

        return new SummaryDto(days,
                recovered.add(open).add(lost), recovered, open, lost,
                total, recoveredCount, count(counts, PaymentStatus.OPEN), count(counts, PaymentStatus.LOST),
                total == 0 ? 0 : Math.round(recoveredCount * 1000.0 / total) / 1000.0,
                alerts.countByStatus(AlertStatus.OPEN),
                duplicates.countByStatus(DuplicateStatus.OPEN),
                retries.countByStatusAndScheduledAtBefore(RetryStatus.PENDING, Instant.now().plus(Duration.ofHours(24))),
                "INR");
    }

    @Cacheable(cacheNames = "analytics-trend", key = "#days")
    public List<TrendPointDto> trend(int days) {
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(days - 1L);
        Map<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            byDay.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        Instant since = first.atStartOfDay(zone).toInstant();
        for (Object[] row : payments.amountsSince(since)) {
            LocalDate day = ((Instant) row[0]).atZone(zone).toLocalDate();
            BigDecimal[] bucket = byDay.get(day);
            if (bucket == null) continue;
            BigDecimal amount = toBigDecimal(row[1]);
            bucket[0] = bucket[0].add(amount);
            if (row[2] == PaymentStatus.RECOVERED) bucket[1] = bucket[1].add(amount);
        }
        return byDay.entrySet().stream()
                .map(e -> new TrendPointDto(e.getKey().toString(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    @Cacheable(cacheNames = "analytics-reasons", key = "#days")
    public List<ReasonCountDto> failureReasons(int days) {
        Instant since = Instant.now().minus(Duration.ofDays(days));
        return payments.reasonBreakdown(since, PaymentStatus.RECOVERED).stream()
                .map(r -> new ReasonCountDto((FailureReason) r[0], (FailureCategory) r[1],
                        ((Number) r[2]).longValue(), r[3] == null ? 0 : ((Number) r[3]).longValue()))
                .toList();
    }

    private static BigDecimal sum(Map<PaymentStatus, BigDecimal> sums, Set<PaymentStatus> statuses) {
        return statuses.stream().map(s -> sums.getOrDefault(s, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static long count(Map<PaymentStatus, long[]> counts, Set<PaymentStatus> statuses) {
        return statuses.stream().mapToLong(s -> counts.getOrDefault(s, new long[]{0})[0]).sum();
    }

    static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        return new BigDecimal(o.toString());
    }
}
