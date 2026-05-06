package com.b101.butterfly.reporting.event.dto;

import com.b101.butterfly.reporting.event.entity.ActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * `POST /api/v1/events/actions` 요청 본문 (butterfly_api_spec_v2.md #19).
 *
 * <p>SDK가 발행하는 사용자 반응 이벤트의 페이로드. project_id, campaign_id 는 본 페이로드에
 * 없고 서비스가 부모 notification_logs 에서 복사 (Rule 2).
 */
public record UserActionIngestRequest(
        @NotNull Long logId,
        Long userId,
        Long deviceId,
        @NotNull ActionType actionType,
        @Size(max = 500) String actionTarget,
        @Size(max = 500) String userAgent,
        @NotNull LocalDateTime actionAt
) {
}
