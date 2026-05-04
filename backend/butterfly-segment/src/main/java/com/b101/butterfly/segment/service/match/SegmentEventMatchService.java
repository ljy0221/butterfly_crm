package com.b101.butterfly.segment.service.match;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.repository.SegmentMemberRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.EventConditionNotReadyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 실시간 매칭 평가 서비스. Phase 6-3 컨슈머가 호출.
 *
 * <p>흐름 (단일 segmentId × 단일 userId):
 * <ol>
 *   <li>Segment 조회 (READY + EVENT-only가 아니면 즉시 reject — 캐시 stale 방어)</li>
 *   <li>{@link EventAggReader#userMatches} 호출 — 단일 사용자 SUM HAVING 검사</li>
 *   <li>매칭 시 segment_members UPSERT — Period별 expires_at 정책 적용</li>
 * </ol>
 *
 * <p>expires_at 정책 (Codex 합의 D3 — v1 단순화 + 한계 명시):
 * <ul>
 *   <li>{@link Period.Cumulative} → NULL (영구 매칭)</li>
 *   <li>{@link Period.Relative}(N days) → matchedAt + N days. 정확한 window 이탈 시점이 아닌
 *       membership TTL 근사값 (마지막 매칭 시점부터 슬라이딩)</li>
 *   <li>{@link Period.Absolute}(start, end) → end 다음날 00:00 KST (exclusive boundary)</li>
 * </ul>
 *
 * <p>UPSERT는 {@code SegmentMemberRepository#upsertMember} — 기존 row 있으면 matched_at 갱신 +
 * expires_at은 GREATEST + Cumulative win (한쪽이 NULL이면 NULL).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentEventMatchService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final SegmentRepository segmentRepository;
    private final SegmentMemberRepository segmentMemberRepository;
    private final EventAggReader eventAggReader;

    /**
     * 단일 segment-user 매칭 평가 + UPSERT. 매칭되면 true 반환.
     *
     * @return true = 매칭되어 UPSERT 수행, false = 미매칭/segment stale
     */
    public boolean evaluateAndUpsert(
            Long projectId, Long segmentId, Long userId, LocalDateTime matchedAt) {
        Optional<Segment> opt = segmentRepository.findById(segmentId);
        if (opt.isEmpty()) {
            log.debug("Segment {} 미존재 (캐시 stale) — skip", segmentId);
            return false;
        }
        Segment segment = opt.get();

        EventNode node = extractEventOnlyNode(segment.getConditionTree());
        if (node == null) {
            log.debug("Segment {} EVENT-only 트리 아님 (캐시 stale) — skip", segmentId);
            return false;
        }

        EventQueryContext context =
                new EventQueryContext(projectId, LocalDate.now(SEOUL), SEOUL);
        boolean matches;
        try {
            matches = eventAggReader.userMatches(node, userId, context);
        } catch (EventConditionNotReadyException e) {
            log.warn("EventAggReader 미준비 (event 모듈 미합류?) — segment {} skip", segmentId);
            return false;
        }
        if (!matches) {
            return false;
        }

        LocalDateTime expiresAt = computeExpiresAt(node.period(), matchedAt);
        segmentMemberRepository.upsertMember(segmentId, userId, matchedAt, expiresAt);
        return true;
    }

    /**
     * Period별 expires_at 계산. v1 단순화 — Relative는 슬라이딩 매칭 시점 + N일, Absolute는
     * end+1day 00:00 KST exclusive, Cumulative는 NULL.
     */
    static LocalDateTime computeExpiresAt(Period period, LocalDateTime matchedAt) {
        return switch (period) {
            case Period.Cumulative ignored -> null;
            case Period.Relative r -> matchedAt.plusDays(r.days());
            case Period.Absolute a -> a.endDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
        };
    }

    private static EventNode extractEventOnlyNode(GroupNode tree) {
        if (tree == null || tree.conditions() == null || tree.conditions().size() != 1) {
            return null;
        }
        ConditionNode child = tree.conditions().get(0);
        return (child instanceof EventNode e) ? e : null;
    }
}
