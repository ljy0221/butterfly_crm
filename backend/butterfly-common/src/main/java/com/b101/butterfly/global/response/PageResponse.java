package com.b101.butterfly.global.response;

import java.util.List;

/**
 * 공통 페이지네이션 응답 포맷 (backend/docs/api/00-common.md).
 *
 * <pre>
 * {
 *   "content": [ ... ],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 150,
 *   "totalPages": 8
 * }
 * </pre>
 *
 * <p>Spring Data의 {@code Page} 타입에 의존하지 않는다 — butterfly-common은 JPA 비의존.
 * 도메인 모듈에서 {@code PageResponse.of(page.getContent(), page.getNumber(), ...)} 형태로 생성하거나,
 * 해당 모듈에 {@code Page → PageResponse} 어댑터 유틸을 두는 것을 권장.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
