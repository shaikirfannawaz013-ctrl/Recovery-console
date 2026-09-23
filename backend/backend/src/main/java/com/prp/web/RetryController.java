package com.prp.web;

import com.prp.service.RetryAdminService;
import com.prp.web.dto.RescheduleRequest;
import com.prp.web.dto.RetryDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/retries")
public class RetryController {

    private final RetryAdminService retries;

    public RetryController(RetryAdminService retries) {
        this.retries = retries;
    }

    @GetMapping
    public List<RetryDto> upcoming() {
        return retries.upcoming();
    }

    @PatchMapping("/{id}")
    public RetryDto reschedule(@PathVariable String id, @Valid @RequestBody RescheduleRequest req) {
        return retries.reschedule(id, req.scheduledAt());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable String id) {
        retries.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
