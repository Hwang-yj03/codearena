package com.codearena.api.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Page 객체를 그대로 JSON으로 내보내면 내부 구조가 API 스펙이 되어버린다.
 * 클라이언트에 필요한 필드만 고정된 형태로 내려준다.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
