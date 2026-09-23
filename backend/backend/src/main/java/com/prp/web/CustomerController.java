package com.prp.web;

import com.prp.domain.RiskBand;
import com.prp.service.CustomerService;
import com.prp.web.dto.CustomerRowDto;
import com.prp.web.dto.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    public PageResponse<CustomerRowDto> list(@RequestParam(required = false) RiskBand riskBand,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return customers.list(riskBand, q, page, size);
    }
}
