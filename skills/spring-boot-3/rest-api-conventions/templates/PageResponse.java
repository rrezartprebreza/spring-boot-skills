package com.example.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {
    public PageResponse {
        content = List.copyOf(content);
    }

    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(source.getContent(), source.getNumber(), source.getSize(),
            source.getTotalElements(), source.getTotalPages(), source.isLast());
    }
}
