package com.b101.butterfly.segment.messaging;

import java.time.Instant;

/**
 * Segment rebuild 요청 Kafka 페이로드. API에서 publish, worker에서 consume.
 *
 * <p>{@code conditionTreeHash}는 Codex 합의 안전장치 — worker가 메시지 수신 시점에 segment의
 * 현재 conditionTree와 비교해 다르면 reject. 빌드 중 conditionTree 수정에 대한 race condition 방지.
 *
 * <p>Kafka 토픽: {@value #TOPIC}, 키: segmentId 문자열.
 *
 * @param segmentId          빌드 대상 segment ID
 * @param projectId          프로젝트 ID (멀티테넌트 격리)
 * @param requestedBy        rebuild 요청 회원 ID (audit / 추적)
 * @param requestedAt        요청 시각 (UTC Instant)
 * @param conditionTreeHash  요청 시점 conditionTree 해시 — worker 처리 시점 비교용
 */
public record SegmentRebuildMessage(
        Long segmentId,
        Long projectId,
        Long requestedBy,
        Instant requestedAt,
        String conditionTreeHash) {

    public static final String TOPIC = "segment.rebuild.requested.v1";
}
