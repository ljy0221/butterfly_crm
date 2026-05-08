package com.b101.butterfly.reporting.event.port;

import java.util.Optional;

/**
 * 사용자 반응 이벤트 API의 X-API-Key 헤더 검증을 위한 SPI.
 *
 * <p>이벤트 도메인은 본 인터페이스를 통해서만 API 키 유효성을 확인하고, 실제 어댑터(tenant
 * 도메인 ProjectRepository 직접 조회)는 butterfly-api 조립 레이어에서 wiring 한다 —
 * ADR-0014 모듈 경계 준수.
 *
 * <p>합의 (2026-05-08, 김동현): 옵션 A 채택. DB(tenant.projects.public_api_key)가 단일
 * 진실 공급원이며 Redis 캐시는 사용하지 않는다 — 재발급/삭제 즉시 반영. 187의
 * {@code CampaignExecutionPort}와 동일한 SPI 위치 패턴.
 *
 * <p>키 포맷: {@code pk_live_} 접두사 + UUID hex 24자 = 총 32자 고정.
 * DB 저장 형태는 SHA-256 해시 64자이므로 어댑터는 평문 키를 동일 알고리즘으로 해싱한 뒤
 * 조회해야 한다 ({@code ApiKeyService.sha256()} 과 정확히 동일).
 */
public interface ApiKeyValidator {

    /**
     * 무효 키이면 {@link Optional#empty()}, 유효하면 키가 소속된 프로젝트 정보 반환.
     *
     * <p>"무효" 의 정의:
     * <ul>
     *   <li>{@code null} 또는 빈 문자열</li>
     *   <li>형태 불일치 (접두사 또는 길이) — DB 조회 회피</li>
     *   <li>해시 일치하는 프로젝트 미존재</li>
     * </ul>
     */
    Optional<ApiKeyInfo> validate(String rawKey);
}
