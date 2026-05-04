package com.b101.butterfly.segment.messaging;

import com.b101.butterfly.segment.service.match.ActiveEventSegmentCache;
import com.b101.butterfly.segment.service.match.SegmentEventMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 실시간 매칭 컨슈머. event 모듈이 "이벤트 저장 + agg 반영 완료" 토픽에 publish하는 메시지를
 * 받아 활성 EVENT 세그먼트들에 대해 매칭 평가 + segment_members UPSERT.
 *
 * <p>흐름:
 * <ol>
 *   <li>dedup: {@code event:dedup:{eventUuid}} Redis SETNX TTL {@value #DEDUP_TTL_MINUTES}분.
 *       이미 처리한 이벤트면 skip.</li>
 *   <li>활성 segment 캐시 lookup: {@code (projectId, categoryId)}로 매칭 후보 segmentIds 조회.</li>
 *   <li>각 segmentId에 대해 {@link SegmentEventMatchService#evaluateAndUpsert} 호출.
 *       userMatches 단일 사용자 SUM HAVING 검사 + Period별 expires_at UPSERT.</li>
 * </ol>
 *
 * <p><b>v1 한계 (Codex 합의 D1)</b>: {@code events.persisted} 토픽은 이벤트 저장 + agg upsert
 * 트랜잭션 커밋 후 publish 보장 — 본 컨슈머가 {@link #SegmentEventMatchConsumer} 시점에서
 * agg 반영 완료를 가정한다. 토픽 협의 진행 중 (`docs/private/request-event-persisted-topic.md`).
 */
@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class SegmentEventMatchConsumer {

    private static final String GROUP_ID = "segment-event-match-worker";
    private static final String DEDUP_KEY_PREFIX = "event:dedup:";
    private static final long DEDUP_TTL_MINUTES = 10;

    private final StringRedisTemplate redisTemplate;
    private final ActiveEventSegmentCache activeEventSegmentCache;
    private final SegmentEventMatchService segmentEventMatchService;

    @KafkaListener(
            topics = EventOccurredMessage.TOPIC,
            groupId = GROUP_ID,
            containerFactory = "eventOccurredKafkaListenerContainerFactory")
    public void consume(EventOccurredMessage message) {
        if (!claimDedup(message.eventUuid())) {
            log.debug("이벤트 {} 이미 처리됨 — skip", message.eventUuid());
            return;
        }

        List<Long> segmentIds =
                activeEventSegmentCache.findActiveSegmentIds(
                        message.projectId(), message.categoryId());
        if (segmentIds.isEmpty()) {
            log.debug(
                    "활성 segment 없음 — projectId={} categoryId={} skip",
                    message.projectId(),
                    message.categoryId());
            return;
        }

        int matched = 0;
        for (Long segmentId : segmentIds) {
            try {
                if (segmentEventMatchService.evaluateAndUpsert(
                        message.projectId(),
                        segmentId,
                        message.userId(),
                        message.occurredAt())) {
                    matched++;
                }
            } catch (RuntimeException e) {
                log.error(
                        "Segment {} 매칭 평가 실패 — eventUuid={}, userId={}",
                        segmentId,
                        message.eventUuid(),
                        message.userId(),
                        e);
                // 다른 segment 평가는 계속 — 한 segment의 일시 오류로 전체 메시지 retry 회피
            }
        }
        log.debug(
                "이벤트 {} 처리 완료 — segments={} matched={}",
                message.eventUuid(),
                segmentIds.size(),
                matched);
    }

    /**
     * dedup 점유. SETNX 패턴 — 키가 없으면 생성하고 true 반환, 이미 있으면 false.
     * Redis 자체 race-free.
     */
    private boolean claimDedup(String eventUuid) {
        String key = DEDUP_KEY_PREFIX + eventUuid;
        Boolean acquired =
                redisTemplate
                        .opsForValue()
                        .setIfAbsent(key, "1", Duration.ofMinutes(DEDUP_TTL_MINUTES));
        return Boolean.TRUE.equals(acquired);
    }
}
