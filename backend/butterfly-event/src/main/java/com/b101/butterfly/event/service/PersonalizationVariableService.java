package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.response.RecommendedVariableResponse;
import com.b101.butterfly.event.repository.UserTagRepository;
import com.b101.butterfly.event.repository.UserTagRepository.RecommendedTagKey;
import com.b101.butterfly.segment.entity.SegmentVariableStats;
import com.b101.butterfly.segment.repository.SegmentVariableStatsRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 템플릿 편집 시 "프로젝트(+세그먼트)별 추천 변수" 목록을 제공.
 *
 * <p>현재 산출 기준: {@code user_tags} 에 실제 적재돼 있는 tag_key 의 빈도 상위 N 개.
 * AI 기반 추천(예: GPT 가 본문 컨텍스트 보고 제안) 은 후속 별 트랙.
 */
@Service
@RequiredArgsConstructor
public class PersonalizationVariableService {

    private static final int DEFAULT_LIMIT = 10;

    private static final List<RecommendedVariableResponse.Item> SYSTEM_VARIABLES = List.of(
            new RecommendedVariableResponse.Item("name",         0, "SYSTEM", "SYSTEM_PROVIDED", 0, 0, "{{name}}"),
            new RecommendedVariableResponse.Item("email",        0, "SYSTEM", "SYSTEM_PROVIDED", 0, 0, "{{email}}"),
            new RecommendedVariableResponse.Item("phone",        0, "SYSTEM", "SYSTEM_PROVIDED", 0, 0, "{{phone}}"),
            new RecommendedVariableResponse.Item("today",        0, "SYSTEM", "SYSTEM_PROVIDED", 0, 0, "{{today}}"),
            new RecommendedVariableResponse.Item("birth_date",   0, "SYSTEM", "SYSTEM_PROVIDED", 0, 0, "{{birth_date}}"),
            new RecommendedVariableResponse.Item("signed_up_at", 0, "SYSTEM", "SYSTEM_PROVIDED", 0, 0, "{{signed_up_at}}")
    );

    private final UserTagRepository userTagRepository;
    private final SegmentVariableStatsRepository segmentVariableStatsRepository;

    @Transactional(readOnly = true)
    public RecommendedVariableResponse getRecommended(Long projectId, Long segmentId) {
        if (segmentId == null) {
            return new RecommendedVariableResponse(SYSTEM_VARIABLES);
        }
        if (segmentId != null) {
            List<SegmentVariableStats> stats =
                    segmentVariableStatsRepository
                            .findByProjectIdAndSegmentIdOrderByVariableKeyAsc(projectId, segmentId);
            if (!stats.isEmpty()) {
                List<RecommendedVariableResponse.Item> items = stats.stream()
                        .limit(DEFAULT_LIMIT)
                        .map(this::toItem)
                        .toList();
                return new RecommendedVariableResponse(items);
            }
        }

        List<RecommendedTagKey> rows = userTagRepository.findRecommendedTagKeys(
                projectId, segmentId, DEFAULT_LIMIT);
        List<RecommendedVariableResponse.Item> items = rows.stream()
                .map(r -> new RecommendedVariableResponse.Item(r.getTagKey(), r.getUserCount()))
                .toList();
        return new RecommendedVariableResponse(items);
    }

    private RecommendedVariableResponse.Item toItem(SegmentVariableStats stats) {
        return new RecommendedVariableResponse.Item(
                stats.getVariableKey(),
                stats.getAvailableCount(),
                stats.getSourceType().name(),
                stats.getStatus().name(),
                stats.getAvailableCount(),
                stats.getMissingCount(),
                "{{" + stats.getVariableKey() + "}}");
    }
}
