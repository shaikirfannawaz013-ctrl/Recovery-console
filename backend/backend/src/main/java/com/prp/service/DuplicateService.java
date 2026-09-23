package com.prp.service;

import com.prp.common.BadRequestException;
import com.prp.common.NotFoundException;
import com.prp.domain.DuplicateCase;
import com.prp.domain.DuplicateStatus;
import com.prp.recovery.GatewayClient;
import com.prp.repository.DuplicateCaseRepository;
import com.prp.web.dto.DuplicateDto;
import com.prp.web.dto.Mappers;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DuplicateService {

    private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "detectedAt");
    private final DuplicateCaseRepository duplicates;
    private final GatewayClient gateway;

    public DuplicateService(DuplicateCaseRepository duplicates, GatewayClient gateway) {
        this.duplicates = duplicates;
        this.gateway = gateway;
    }

    @Transactional(readOnly = true)
    public List<DuplicateDto> list(DuplicateStatus status) {
        var list = status == null ? duplicates.findAllBy(NEWEST) : duplicates.findByStatus(status, NEWEST);
        return list.stream().map(Mappers::duplicate).toList();
    }

    @Transactional
    public DuplicateDto resolve(String id, String decision, String user) {
        DuplicateCase d = duplicates.findById(id).orElseThrow(() -> new NotFoundException("Duplicate record not found."));
        if (d.getStatus() != DuplicateStatus.OPEN) throw new BadRequestException("This duplicate was already resolved.");
        switch (decision.toUpperCase()) {
            case "REFUND" -> {
                gateway.refund(d.getDuplicateId(), d.getAmount());
                d.setStatus(DuplicateStatus.REFUNDED);
            }
            case "KEEP" -> d.setStatus(DuplicateStatus.NOT_DUPLICATE);
            default -> throw new BadRequestException("Decision must be REFUND or KEEP.");
        }
        d.setResolvedBy(user);
        return Mappers.duplicate(d);
    }
}
