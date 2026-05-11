package com.b101.butterfly.event.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 이벤트 카테고리 생성 요청.
 *
 * <p>{@code categoryName} 은 SDK 트래킹 키 컨벤션 — 영소문자/숫자/언더스코어 3~40자, 선두는
 * 소문자/언더스코어 (frontend 의 {@code categoryNameSchema} 와 동일 정규식).
 *
 * <p>{@code displayName} 은 사용자 표시명 (필수). backend 의 {@code description} 컬럼에 저장된다 —
 * frontend 가 보내는 {@code description} 필드는 같은 컬럼을 공유하므로 displayName 이 비어있을 때만
 * fallback 으로 사용한다.
 *
 * <p>{@code properties} 는 초기 schema 정의 — 비어 있어도 OK (이후 PUT /schema 로 채울 수 있음).
 */
public record CreateCategoryRequest(
        @NotBlank(message = "categoryName 은 필수입니다.")
        @Pattern(
                regexp = "^[a-z_][a-z0-9_]{2,39}$",
                message = "categoryName 은 영소문자/숫자/언더스코어 3~40자 (선두는 소문자/언더스코어) 여야 합니다.")
        String categoryName,

        @NotBlank(message = "displayName 은 필수입니다.")
        @Size(max = 500, message = "displayName 은 500자 이하여야 합니다.")
        String displayName,

        @Size(max = 500, message = "description 은 500자 이하여야 합니다.")
        String description,

        @Valid
        List<PropertyRequest> properties) {

    /**
     * backend {@code description} 컬럼에 저장할 텍스트 — displayName 우선, 없으면 description.
     */
    public String resolveDescription() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return description;
    }
}
