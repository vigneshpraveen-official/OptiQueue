package com.optiqueue.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable, cache-friendly page envelope. Spring's PageImpl is not designed for
 * JSON serialization round-trips (needed when pages are stored in Redis), so
 * cached endpoints return this instead.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
