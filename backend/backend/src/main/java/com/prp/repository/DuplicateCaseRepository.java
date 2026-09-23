package com.prp.repository;

import com.prp.domain.DuplicateCase;
import com.prp.domain.DuplicateStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DuplicateCaseRepository extends JpaRepository<DuplicateCase, String> {

    @EntityGraph(attributePaths = "customer")
    List<DuplicateCase> findByStatus(DuplicateStatus status, Sort sort);

    @EntityGraph(attributePaths = "customer")
    List<DuplicateCase> findAllBy(Sort sort);

    long countByStatus(DuplicateStatus status);
}
