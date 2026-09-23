package com.prp.web;

import com.prp.domain.DuplicateStatus;
import com.prp.service.DuplicateService;
import com.prp.web.dto.DecisionRequest;
import com.prp.web.dto.DuplicateDto;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/duplicates")
public class DuplicateController {

    private final DuplicateService duplicates;

    public DuplicateController(DuplicateService duplicates) {
        this.duplicates = duplicates;
    }

    @GetMapping
    public List<DuplicateDto> list(@RequestParam(required = false) DuplicateStatus status) {
        return duplicates.list(status);
    }

    @PostMapping("/{id}/resolve")
    public DuplicateDto resolve(@PathVariable String id, @Valid @RequestBody DecisionRequest req, Authentication auth) {
        return duplicates.resolve(id, req.decision(), auth.getName());
    }
}
