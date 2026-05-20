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
 * <p>고객사 앱 클라이언트가 보낸 페이로드를 받아 부모 notification_logs를 검증하고 비정규화 필드
 * (project_id, campaign_id)를 복사한 뒤 user_action_logs에 INSERT한다. 이벤트 로그 특성상
 * 중복 적재를 허용한다 (SCHEMA_DESIGN.md Rule 2).
 *
 * <p><b>도메인 검증 (ADR-0017)</b>: 호출자가 인증 필터에서 해석한 {@code resolvedProjectId}를
 * 넘겨주면, 부모 notification_logs 의 project_id 와 일치하는지 비교한다. 불일치 시 다른
 * 프로젝트 logId 의 존재 사실 자체를 노출하지 않기 위해 401/403 이 아닌
 * {@link AnalyticsErrorCode#LOG_NOT_FOUND} (400) 으로 통일한다.
 *
 * <p>인증/레이트 리밋은 `ApiKeyAuthenticationFilter`(역할 1, S14P31B101-205) 가 담당하고,
 * 본 서비스는 도메인 레벨 검증만 수행한다.
 */
@Service
@RequiredArgsConstructor
public class UserActionIngestService {

    private final UserActionLogRepository userActionLogRepository;
    private final ReportingNotificationLogRepository notificationLogRepository;

    /**
     * @param resolvedProjectId 인증 필터가 X-API-Key 로 해석한 projectId. {@code null} 이면
     *                          도메인 검증을 건너뛴다 (인증 미적용 환경 — 통합 테스트 등).
     *                          운영 흐름에서는 항상 non-null 이어야 한다.
     */
    @Transactional
    public UserActionLog record(UserActionIngestRequest request, Long resolvedProjectId) {
        NotificationLog parent = resolveParent(request);

        // 도메인 검증: API Key 소속 projectId 와 부모 logId.projectId 가 일치해야 한다.
        // 다른 프로젝트 logId 노출 방지 (ADR-0017) — 동일 LOG_NOT_FOUND 코드로 통일.
        if (resolvedProjectId != null && !resolvedProjectId.equals(parent.getProjectId())) {
            throw new BusinessException(AnalyticsErrorCode.LOG_NOT_FOUND);
        }

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

    private NotificationLog resolveParent(UserActionIngestRequest request) {
        if (request.logId() != null) {
            return notificationLogRepository.findById(request.logId())
                    .orElseThrow(() -> new BusinessException(AnalyticsErrorCode.LOG_NOT_FOUND));
        }
        if (request.messageKey() != null && !request.messageKey().isBlank()) {
            return notificationLogRepository.findByMessageKey(request.messageKey())
                    .orElseThrow(() -> new BusinessException(AnalyticsErrorCode.LOG_NOT_FOUND));
        }
        throw new BusinessException(AnalyticsErrorCode.LOG_NOT_FOUND);
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
