package com.b101.butterfly.segment.service.preview;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import com.b101.butterfly.segment.dto.response.PreviewResponse;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.UserDirectoryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;

/**
 * 미리보기 서비스 (#42). TAG/USER/DEVICE 한정 — EVENT 포함 시 SEGMENT_501_EVENT_NOT_READY.
 *
 * <p>흐름: ConditionTreeValidator → canonical hash → 캐시 조회 → SQL 변환 → user_id 조회 → HLL 카운트 + TTL 캐시.
 */
@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final Duration HLL_TTL = Duration.ofHours(24);
    private static final String METHOD_HLL = "HLL";

    private final ConditionTreeValidator conditionTreeValidator;
    private final ConditionTreeToSqlConverter sqlConverter;
    private final UserDirectoryReader userDirectoryReader;
    private final HllPreviewExecutor hllPreviewExecutor;
    private final ConditionTreeHasher hasher;

    public PreviewResponse preview(Long projectId, GroupNode tree) {
        conditionTreeValidator.validate(tree);

        String hash = hasher.hash(projectId, tree);

        OptionalLong cached = hllPreviewExecutor.getCachedCount(hash);
        if (cached.isPresent()) {
            return new PreviewResponse((int) cached.getAsLong(), METHOD_HLL, LocalDateTime.now());
        }

        SqlFragment fragment;
        try {
            fragment = sqlConverter.convert(tree, projectId);
        } catch (EventConditionNotReadyException e) {
            throw new BusinessException(SegmentErrorCode.SEGMENT_501_EVENT_NOT_READY);
        }

        List<Long> userIds =
                userDirectoryReader.queryUserIds(fragment.sql(), fragment.params());
        long count = hllPreviewExecutor.computeAndCache(hash, userIds, HLL_TTL);

        return new PreviewResponse((int) count, METHOD_HLL, LocalDateTime.now());
    }
}
