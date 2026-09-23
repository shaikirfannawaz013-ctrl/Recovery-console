package com.prp.recovery;

import com.prp.config.AppProperties;
import com.prp.domain.RetrySchedule;
import com.prp.domain.RetryStatus;
import com.prp.repository.RetryScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls for retries that are due. A Redis lock per retry means several backend
 * instances can run this job without charging a customer twice.
 */
@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);
    private final RetryScheduleRepository retries;
    private final RetryService retryService;
    private final StringRedisTemplate redis;
    private final AppProperties props;

    public RetryExecutor(RetryScheduleRepository retries, RetryService retryService,
                         StringRedisTemplate redis, AppProperties props) {
        this.retries = retries;
        this.retryService = retryService;
        this.redis = redis;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.retry.poll-interval-ms}", initialDelay = 10_000)
    public void runDueRetries() {
        List<RetrySchedule> due = retries.findByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                RetryStatus.PENDING, Instant.now(), PageRequest.of(0, props.retry().batchSize()));
        for (RetrySchedule r : due) {
            String lock = "lock:retry:" + r.getId();
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, "1", Duration.ofMinutes(2)))) continue;
            try {
                retryService.executeScheduled(r.getId());
            } catch (ObjectOptimisticLockingFailureException e) {
                log.info("Retry {} skipped: payment changed at the same time", r.getId());
            } catch (Exception e) {
                log.error("Retry {} failed to run", r.getId(), e);
            } finally {
                redis.delete(lock);
            }
        }
    }
}
