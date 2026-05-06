package com.b101.butterfly.reporting.event.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.reporting.event.dto.UserActionIngestRequest;
import com.b101.butterfly.reporting.event.entity.ActionType;
import com.b101.butterfly.reporting.event.entity.UserActionLog;
import com.b101.butterfly.reporting.event.repository.UserActionLogRepository;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.entity.NotificationLog;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 사용자 반응 이벤트 적재 서비스 (S14P31B101-123 / 123-A).
 *
 * <p>SDK가 보낸 페이로드를 받아 부모 notification_logs를 검증하고 비정규화 필드(project_id,
 * campaign_id)를 복사한 뒤 user_action_logs에 INSERT한다. 이벤트 로그 특성상 중복 적재를
 * 허용한다 (SCHEMA_DESIGN.md Rule 2).
 *
 * <p>인증 / 레이트 리밋 / 부하 검증은 본 티켓 범위 외 — `S14P31B101-193` (123-B) 에서 추가된다.
 */
@Service
@RequiredArgsConstructor
public class UserActionIngestService {

    private final UserActionLogRepository userActionLogRepository;
    private final ReportingNotificationLogRepository notificationLogRepository;

    @Transactional
    public UserActionLog record(UserActionIngestRequest request) {
        NotificationLog parent = notificationLogRepository.findById(request.logId())
                .orElseThrow(() -> new BusinessException(AnalyticsErrorCode.LOG_NOT_FOUND));

        if (request.actionType() == ActionType.CLICK) {
            validateUrl(request.actionTarget());
        }

        UserActionLog row = UserActionLog.create()
                .logId(parent.getId())
                .projectId(parent.getProjectId())
                .campaignId(parent.getCampaignId())
                .userId(request.userId())
                .deviceId(request.deviceId())
                .actionType(request.actionType())
                .actionTarget(request.actionTarget())
                .userAgent(request.userAgent())
                .actionAt(request.actionAt())
                .build();

        return userActionLogRepository.save(row);
    }

    private static void validateUrl(String actionTarget) {
        if (actionTarget == null || actionTarget.isBlank()) {
            // CLICK인데 target이 없으면 의미가 없다 — INVALID로 분류.
            throw new BusinessException(AnalyticsErrorCode.INVALID_ACTION_TARGET);
        }
        try {
            URI uri = new URI(actionTarget);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BusinessException(AnalyticsErrorCode.INVALID_ACTION_TARGET);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException(AnalyticsErrorCode.INVALID_ACTION_TARGET);
            }
        } catch (URISyntaxException e) {
            throw new BusinessException(AnalyticsErrorCode.INVALID_ACTION_TARGET);
        }
    }
}
