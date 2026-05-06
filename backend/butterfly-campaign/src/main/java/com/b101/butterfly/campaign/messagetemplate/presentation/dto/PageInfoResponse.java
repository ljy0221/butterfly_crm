package com.b101.butterfly.campaign.messagetemplate.presentation.dto;

import org.springframework.data.domain.Page;

public record PageInfoResponse(
        long totalElements,
        int totalPages,
        int currentPage,
        boolean isLast
) {

    public static PageInfoResponse from(Page<?> page) {
        return new PageInfoResponse(
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.isLast()
        );
    }
}
