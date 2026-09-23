package com.prp.web;

import com.prp.domain.AlertStatus;
import com.prp.service.FraudService;
import com.prp.web.dto.DecisionRequest;
import com.prp.web.dto.FraudAlertDto;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fraud/alerts")
public class FraudController {

    private final FraudService fraud;

    public FraudController(FraudService fraud) {
        this.fraud = fraud;
    }

    @GetMapping
    public List<FraudAlertDto> list(@RequestParam(required = false) AlertStatus status) {
        return fraud.list(status);
    }

    @PostMapping("/{id}/resolve")
    public FraudAlertDto resolve(@PathVariable String id, @Valid @RequestBody DecisionRequest req, Authentication auth) {
        return fraud.resolve(id, req.decision(), auth.getName());
    }
}
