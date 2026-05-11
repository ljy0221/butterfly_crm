package com.b101.butterfly.event.dto.response;

/**
 * 카테고리 삭제 응답 — frontend 가 삭제된 categoryId 만 확인하면 되도록 단일 필드.
 */
public record DeleteCategoryResponse(Long categoryId) {
}
