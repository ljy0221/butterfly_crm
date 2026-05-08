package com.b101.butterfly.reporting.event.port;

/**
 * API 키가 유효할 때 반환되는 최소 정보.
 *
 * <p>이벤트 도메인은 {@code projectId} 만 필요하다 — 그 외 메타(키 자체·이름·발급일 등)는
 * 노출하지 않는다 (정보 최소화 원칙).
 */
public record ApiKeyInfo(Long projectId) {}
