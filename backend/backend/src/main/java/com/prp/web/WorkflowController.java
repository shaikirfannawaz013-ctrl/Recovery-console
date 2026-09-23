package com.prp.web;

import com.prp.service.WorkflowService;
import com.prp.web.dto.WorkflowDto;
import com.prp.web.dto.WorkflowUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** PUT is restricted to ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflows;

    public WorkflowController(WorkflowService workflows) {
        this.workflows = workflows;
    }

    @GetMapping
    public List<WorkflowDto> list() {
        return workflows.list();
    }

    @PutMapping("/{id}")
    public WorkflowDto update(@PathVariable String id, @Valid @RequestBody WorkflowUpdateRequest req) {
        return workflows.update(id, req);
    }
}
