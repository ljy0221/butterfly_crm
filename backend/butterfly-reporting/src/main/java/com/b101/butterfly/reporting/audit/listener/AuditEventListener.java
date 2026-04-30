package com.b101.butterfly.reporting.audit.listener;

import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.b101.butterfly.reporting.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 감사 이벤트 구독자 (ADR-0009).
 *
 * <p>모든 도메인이 발행한 {@link AuditRecordedEvent}를 받아 reporting 소유의 {@code audit_logs}
 * 테이블에 저장한다. 발행자 트랜잭션이 commit된 뒤에만 실행되도록
 * {@link TransactionPhase#AFTER_COMMIT}을 사용한다 — 발행자 트랜잭션이 롤백되면 audit도
 * 기록되지 않는다(이게 정상 동작).
 *
 * <p><b>실패 처리</b>: AFTER_COMMIT 단계에서 발생한 예외는 발행자 트랜잭션을 되돌리지 못한다.
 * audit 저장 실패는 로그로 남기고 운영 모니터링에 의존한다 (audit 시스템이 비즈니스 로직을
 * 깨뜨리지 않도록 의도적 설계). 향후 신뢰도 강화가 필요하면 Outbox 패턴 도입 검토.
 */
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(AuditRecordedEvent event) {
        AuditLog auditLog = AuditLog.create(
                event.projectId(),
                event.actorId(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.detail(),
                event.ipAddress(),
                event.userAgent()
        );
        auditLogRepository.save(auditLog);
    }
}
