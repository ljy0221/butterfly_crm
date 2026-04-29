package com.b101.butterfly.segment.dto.response;

import java.time.LocalDateTime;

/**
 * 미리보기 응답 (#42). method는 HLL(근사) 또는 EXACT(실측).
 */
public record PreviewResponse(int estimatedCount, String method, LocalDateTime estimatedAt) {
}
