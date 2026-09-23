package com.prp.service;

import com.prp.domain.Customer;
import com.prp.domain.PaymentStatus;
import com.prp.domain.RiskBand;
import com.prp.repository.CustomerRepository;
import com.prp.repository.PaymentRepository;
import com.prp.web.dto.CustomerRowDto;
import com.prp.web.dto.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final PaymentRepository payments;

    public CustomerService(CustomerRepository customers, PaymentRepository payments) {
        this.customers = customers;
        this.payments = payments;
    }

    public PageResponse<CustomerRowDto> list(RiskBand band, String q, int page, int size) {
        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (band != null) ps.add(cb.equal(root.get("riskBand"), band));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("id")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Customer> result = customers.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "riskScore").and(Sort.by("id"))));

        // one grouped query for the whole page instead of N queries
        Map<String, Object[]> stats = new HashMap<>();
        List<String> ids = result.getContent().stream().map(Customer::getId).toList();
        if (!ids.isEmpty()) {
            payments.statsForCustomers(ids, PaymentStatus.RECOVERED, PaymentStatus.OPEN)
                    .forEach(row -> stats.put((String) row[0], row));
        }
        return PageResponse.of(result, c -> {
            Object[] s = stats.get(c.getId());
            long failed = s == null ? 0 : ((Number) s[1]).longValue();
            long recovered = s == null || s[2] == null ? 0 : ((Number) s[2]).longValue();
            BigDecimal open = s == null ? BigDecimal.ZERO : AnalyticsService.toBigDecimal(s[3]);
            return new CustomerRowDto(c.getId(), c.getName(), c.getEmail(), c.getCity(), c.getRiskScore(),
                    c.getRiskBand(), c.getMemberSince(), c.getTotalPayments(), failed,
                    failed == 0 ? null : Math.round(recovered * 100.0 / failed) / 100.0, open);
        });
    }
}
