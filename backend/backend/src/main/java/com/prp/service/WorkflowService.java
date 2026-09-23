package com.prp.service;

import com.prp.common.NotFoundException;
import com.prp.domain.RecoveryWorkflow;
import com.prp.repository.RecoveryWorkflowRepository;
import com.prp.web.dto.Mappers;
import com.prp.web.dto.WorkflowDto;
import com.prp.web.dto.WorkflowUpdateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkflowService {

    private final RecoveryWorkflowRepository workflows;

    public WorkflowService(RecoveryWorkflowRepository workflows) {
        this.workflows = workflows;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDto> list() {
        return workflows.findAll(Sort.by("id")).stream().map(Mappers::workflow).toList();
    }

    @Transactional
    public WorkflowDto update(String id, WorkflowUpdateRequest req) {
        RecoveryWorkflow w = workflows.findById(id).orElseThrow(() -> new NotFoundException("Rule not found."));
        if (req.enabled() != null) w.setEnabled(req.enabled());
        if (req.maxAttempts() != null) w.setMaxAttempts(req.maxAttempts());
        if (req.backoffHours() != null) w.setBackoffHours(req.backoffHours());
        if (req.minRecoveryProbability() != null) w.setMinRecoveryProbability(req.minRecoveryProbability());
        return Mappers.workflow(w);
    }
}
