package com.prp.service;

import com.prp.common.BadRequestException;
import com.prp.common.NotFoundException;
import com.prp.domain.PaymentStatus;
import com.prp.domain.RetrySchedule;
import com.prp.domain.RetryStatus;
import com.prp.repository.RetryScheduleRepository;
import com.prp.web.dto.Mappers;
import com.prp.web.dto.RetryDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RetryAdminService {

    private final RetryScheduleRepository retries;

    public RetryAdminService(RetryScheduleRepository retries) {
        this.retries = retries;
    }

    @Transactional(readOnly = true)
    public List<RetryDto> upcoming() {
        return retries.findByStatusOrderByScheduledAtAsc(RetryStatus.PENDING).stream().map(Mappers::retry).toList();
    }

    @Transactional
    public RetryDto reschedule(String id, Instant at) {
        RetrySchedule r = pending(id);
        if (!at.isAfter(Instant.now())) throw new BadRequestException("Pick a time in the future.");
        r.setScheduledAt(at);
        r.setTimingNote("Rescheduled manually");
        return Mappers.retry(r);
    }

    @Transactional
    public void cancel(String id) {
        RetrySchedule r = pending(id);
        r.setStatus(RetryStatus.CANCELLED);
        if (r.getPayment().getStatus() == PaymentStatus.RETRY_SCHEDULED) {
            r.getPayment().setStatus(PaymentStatus.FAILED);
        }
    }

    private RetrySchedule pending(String id) {
        RetrySchedule r = retries.findById(id).orElseThrow(() -> new NotFoundException("This retry no longer exists."));
        if (r.getStatus() != RetryStatus.PENDING) throw new BadRequestException("This retry has already run or was cancelled.");
        return r;
    }
}
