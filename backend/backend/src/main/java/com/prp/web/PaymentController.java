package com.prp.web;

import com.prp.domain.FailureCategory;
import com.prp.domain.PaymentStatus;
import com.prp.service.PaymentService;
import com.prp.web.dto.ActionRequest;
import com.prp.web.dto.PageResponse;
import com.prp.web.dto.PaymentDetailDto;
import com.prp.web.dto.PaymentSummaryDto;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public PageResponse<PaymentSummaryDto> list(@RequestParam(required = false) PaymentStatus status,
                                                @RequestParam(required = false) FailureCategory category,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "failedAt") String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return payments.search(status, category, q, sort, page, size);
    }

    @GetMapping("/{id}")
    public PaymentDetailDto get(@PathVariable String id) {
        return payments.get(id);
    }

    @PostMapping("/{id}/retry")
    public PaymentDetailDto retry(@PathVariable String id) {
        return payments.retryNow(id);
    }

    @PostMapping("/{id}/actions")
    public PaymentDetailDto action(@PathVariable String id, @Valid @RequestBody ActionRequest req, Authentication auth) {
        return payments.runAction(id, req.action(), auth.getName());
    }
}
