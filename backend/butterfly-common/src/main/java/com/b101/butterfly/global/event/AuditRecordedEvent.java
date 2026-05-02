package com.b101.butterfly.global.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 감사 로그 기록 이벤트.
 *
 * <p>ADR-0009 결정에 따라 모든 도메인은 자기 자원 변경을 직접 기록하지 않고 본 이벤트를 발행한다.
 * reporting 모듈의 {@code AuditEventListener}가 구독하여 {@code audit_logs} 테이블에 저장한다.
 *
 * <p><b>발행 규약</b>: 발행자의 트랜잭션이 commit된 뒤 실제 저장이 일어나도록 구독 측에서
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}을 사용한다. 따라서 발행자
 * 트랜잭션이 롤백되면 audit도 기록되지 않는다(이게 정상 동작).
 *
 * <p><b>비밀값 처리</b>: ADR-0008 §"비밀값 마스킹 규칙" 준수. API Key 원문, FCM credential,
 * 비밀번호, 세션 토큰 등은 {@code detail}에 절대 평문 저장 금지. 변경 추적은 SHA-256 해시 앞 8자만.
 *
 * @param eventId    이벤트 식별자 (null 시 자동 생성)
 * @param occurredAt 발생 시각 (null 시 현재 시각)
 * @param projectId  프로젝트 ID (시스템 레벨 시 null)
 * @param actorId    행위자 회원 ID
 * @param action     액션 코드 (예: {@code FCM_KEY_UPDATE}, {@code APPLICATION_APPROVE})
 * @param targetType 대상 엔티티 타입 (예: {@code "Application"}, {@code "Campaign"})
 * @param targetId   대상 엔티티 ID
 * @param ipAddress  요청 IP (IPv6 대응, 컨트롤러에서 추출 후 전달)
 * @param userAgent  요청 User-Agent
 * @param detail     변경 상세 (변경 전/후 값 등). JSON 직렬화 가능한 Map
 */
public record AuditRecordedEvent(
        UUID eventId,
        Instant occurredAt,
        Long projectId,
        Long actorId,
        String action,
        String targetType,
        Long targetId,
        String ipAddress,
        String userAgent,
        Map<String, Object> detail
) implements DomainEvent {

    /**
     * 메타데이터 자동 채움 canonical constructor.
     * {@code eventId}와 {@code occurredAt}을 호출자가 null로 두면 자동 생성한다.
     */
    public AuditRecordedEvent {
        if (eventId == null) {
            eventId = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }

    /**
     * 자동 메타데이터로 발행할 때 쓰는 편의 팩터리.
     */
    public static AuditRecordedEvent of(
            Long projectId,
            Long actorId,
            String action,
            String targetType,
            Long targetId,
            String ipAddress,
            String userAgent,
            Map<String, Object> detail
    ) {
        return new AuditRecordedEvent(
                null, null, projectId, actorId, action, targetType, targetId, ipAddress, userAgent, detail
        );
    }
}
