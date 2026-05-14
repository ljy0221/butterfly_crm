package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import com.b101.butterfly.segment.dto.response.PreviewResponse;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.EventAggReader;
import com.b101.butterfly.segment.port.EventQueryContext;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;

/**
 * 미리보기 서비스 (#42).
 *
 * <p>filter 없는 EVENT 단독 트리는 EventAggReader port 호출, 그 외 트리는
 * ConditionTreeToSqlConverter가 event_daily_agg/events EXISTS 서브쿼리를 포함한 SQL로 처리한다.
 *
 * <p>흐름:
 * <ul>
 *     <li>validate → hash → 캐시 조회 → 분기 (EVENT-only 또는 SQL) → HLL 카운트 + TTL 캐시</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final Duration HLL_TTL = Duration.ofHours(24);
    private static final String METHOD_HLL = "HLL";
    /** S14P31B101-275 — SQL count 흐름은 정확값. frontend 가 "정확 (EXACT)" 로 표시. */
    private static final String METHOD_EXACT = "EXACT";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ConditionTreeValidator conditionTreeValidator;
    private final ConditionTreeToSqlConverter sqlConverter;
    private final UserDirectoryReader userDirectoryReader;
    private final EventAggReader eventAggReader;
    private final HllPreviewExecutor hllPreviewExecutor;
    private final ConditionTreeHasher hasher;

    public PreviewResponse preview(Long projectId, GroupNode tree) {
        conditionTreeValidator.validate(tree);

        String hash = hasher.hash(projectId, tree);

        EventQueryContext context =
                new EventQueryContext(projectId, LocalDate.now(SEOUL), SEOUL);

        // path 별로 다른 캐시 정책. SQL path 는 exact 만, EventPort path 는 HLL 가능.
        if (isFilterlessEventOnlyTree(tree)) {
            // event_daily_agg 사전 집계 흐름 — 결과가 작아 list → HLL 캐시 유지 (S14P31B101-199 패턴).
            var cached = hllPreviewExecutor.getCached(hash);
            if (cached.isPresent()) {
                return new PreviewResponse(
                        (int) cached.get().count(), cached.get().method(), LocalDateTime.now());
            }
            List<Long> userIds =
                    resolveByEventPort((EventNode) tree.conditions().get(0), context);
            long count = hllPreviewExecutor.computeAndCache(hash, userIds, HLL_TTL);
            return new PreviewResponse((int) count, METHOD_HLL, LocalDateTime.now());
        }

        // SQL count 흐름 (S14P31B101-275) — exact 캐시 만 조회. HLL 잔재 캐시는 무시하고 정확값 재계산.
        OptionalLong cachedExact = hllPreviewExecutor.getCachedExact(hash);
        if (cachedExact.isPresent()) {
            return new PreviewResponse(
                    (int) cachedExact.getAsLong(), METHOD_EXACT, LocalDateTime.now());
        }
        long count = resolveBySqlCount(tree, context);
        hllPreviewExecutor.cacheExactCount(hash, count, HLL_TTL);
        return new PreviewResponse((int) count, METHOD_EXACT, LocalDateTime.now());
    }

    private List<Long> resolveByEventPort(EventNode eventNode, EventQueryContext context) {
        try (Stream<Long> stream = eventAggReader.queryMatchingUserIds(eventNode, context)) {
            return stream.toList();
        } catch (EventConditionNotReadyException e) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_501_EVENT_NOT_READY);
        }
    }

    private long resolveBySqlCount(GroupNode tree, EventQueryContext context) {
        SqlFragment fragment;
        try {
            fragment = sqlConverter.convert(tree, context);
        } catch (EventConditionNotReadyException e) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_501_EVENT_NOT_READY);
        }
        return userDirectoryReader.countUserMatches(fragment.sql(), fragment.params());
    }

    private static boolean isFilterlessEventOnlyTree(GroupNode tree) {
        return tree.conditions().size() == 1
                && tree.conditions().get(0) instanceof EventNode eventNode
                && (eventNode.filters() == null || eventNode.filters().isEmpty());
    }
}
