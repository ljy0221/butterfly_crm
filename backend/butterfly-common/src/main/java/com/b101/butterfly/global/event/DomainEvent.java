package com.b101.butterfly.global.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 도메인 이벤트의 공통 부모 타입.
 *
 * <p>모든 cross-module 이벤트는 이 인터페이스를 구현하고, {@code permits} 절에 record로 등록한다.
 * Spring의 {@code ApplicationEventPublisher}로 발행하고
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 구독하는 것을 기본 패턴으로 한다.
 *
 * <p>이벤트는 {@code butterfly-common}에 위치시켜 모든 도메인 모듈이 발행/구독에 사용할 수 있다.
 * 도메인 모듈 내부 이벤트(같은 모듈 안에서만 도는 이벤트)는 해당 모듈 패키지에 두어도 된다.
 *
 * <p>P6 (모듈러 모놀리스 전환 - 도메인 이벤트 인프라). 최소 범위로 시작해서
 * 사용처가 결정될 때 이벤트 record를 추가한다.
 *
 * @see AuditRecordedEvent
 */
public sealed interface DomainEvent permits AuditRecordedEvent {

    /**
     * 이벤트 고유 식별자. 디버깅·로그 추적·중복 발행 감지에 쓴다.
     * 발행자가 명시 안 하면 record canonical constructor에서 {@code UUID.randomUUID()} 기본값.
     */
    UUID eventId();

    /**
     * 이벤트가 발생한 시점. 발행자가 명시 안 하면 record canonical constructor에서
     * {@code Instant.now()} 기본값.
     */
    Instant occurredAt();

    /**
     * 이벤트가 속한 프로젝트 ID. 시스템 레벨 이벤트(예: 신청서 승인)는 null 허용.
     */
    Long projectId();
}
