package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.ConditionNode;
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
 * <p>v1 EVENT 정책: 단독 트리(GroupNode 안에 EventNode 1개)만 지원. mixed(다른 노드와 결합)는 reject.
 * EVENT 단독 트리는 EventAggReader port 호출, 그 외 트리는 ConditionTreeToSqlConverter SQL 흐름.
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

        OptionalLong cached = hllPreviewExecutor.getCachedCount(hash);
        if (cached.isPresent()) {
            return new PreviewResponse((int) cached.getAsLong(), METHOD_HLL, LocalDateTime.now());
        }

        List<Long> userIds = resolveUserIds(projectId, tree);
        long count = hllPreviewExecutor.computeAndCache(hash, userIds, HLL_TTL);

        return new PreviewResponse((int) count, METHOD_HLL, LocalDateTime.now());
    }

    private List<Long> resolveUserIds(Long projectId, GroupNode tree) {
        if (isEventOnlyTree(tree)) {
            return resolveByEventPort(projectId, (EventNode) tree.conditions().get(0));
        }
        if (containsEventNode(tree)) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_400_EVENT_MIXED_NOT_SUPPORTED_V1);
        }
        return resolveBySql(projectId, tree);
    }

    private List<Long> resolveByEventPort(Long projectId, EventNode eventNode) {
        EventQueryContext context = new EventQueryContext(
                projectId, LocalDate.now(SEOUL), SEOUL);
        try (Stream<Long> stream = eventAggReader.queryMatchingUserIds(eventNode, context)) {
            return stream.toList();
        } catch (EventConditionNotReadyException e) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_501_EVENT_NOT_READY);
        }
    }

    private List<Long> resolveBySql(Long projectId, GroupNode tree) {
        SqlFragment fragment;
        try {
            fragment = sqlConverter.convert(tree, projectId);
        } catch (EventConditionNotReadyException e) {
            // 방어적 — 트리 분기로 이미 EVENT 노드는 SQL converter에 도달하지 않음
            throw new BusinessException(SegmentErrorCode.SEGMENT_501_EVENT_NOT_READY);
        }
        return userDirectoryReader.queryUserIds(fragment.sql(), fragment.params());
    }

    private static boolean isEventOnlyTree(GroupNode tree) {
        return tree.conditions().size() == 1
                && tree.conditions().get(0) instanceof EventNode;
    }

    private static boolean containsEventNode(ConditionNode node) {
        if (node instanceof EventNode) {
            return true;
        }
        if (node instanceof GroupNode g) {
            for (ConditionNode child : g.conditions()) {
                if (containsEventNode(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
