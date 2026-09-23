package com.prp.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Stable page shape for the UI (independent of Spring's internal Page serialisation). */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), Math.max(1, page.getTotalPages()), page.getNumber(), page.getSize());
    }
}
