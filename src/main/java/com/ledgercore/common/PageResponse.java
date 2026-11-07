package com.ledgercore.common;

import org.springframework.data.domain.Page;

import java.util.List;

/** The JSON shape of every paged list in the API. We define it ourselves so it stays stable. */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
