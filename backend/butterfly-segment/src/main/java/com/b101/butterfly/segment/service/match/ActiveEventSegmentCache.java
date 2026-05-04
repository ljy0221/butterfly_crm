package com.b101.butterfly.segment.service.match;

import com.b101.butterfly.segment.domain.condition.ConditionNode;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 활성 EVENT 세그먼트 캐시. 실시간 매칭 컨슈머에서 (projectId, categoryId)로 조회.
 *
 * <p>키 정책:
 * <ul>
 *   <li>{@code seg:active:{projectId}} — JSON {@code Map<categoryId, List<segmentId>>}
 *       (project 단위 단일 키 — 1번 로드로 모든 카테고리 채움. lookup마다 전체 segment 재로딩 회피)
 *   <li>TTL {@value #TTL_SECONDS}초
 * </ul>
 *
 * <p>"활성 EVENT segment" = {@code BuildStatus=READY} + soft-delete 안 됨 + EVENT-only 트리
 * (단일 EventNode가 root group의 유일한 자식). mixed 트리는 v1 미지원이라 제외.
 *
 * <p>무효화: {@link #invalidate(Long)} — segment 변경/삭제/빌드 완료 시 호출자가 명시 호출.
 * TTL 60s만 의존하면 삭제된 segment가 최대 60초 더 매칭되므로 명시 무효화 보강 (Codex 합의 D2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveEventSegmentCache {

    private static final String KEY_PREFIX = "seg:active:";
    private static final long TTL_SECONDS = 60;
    private static final TypeReference<Map<Long, List<Long>>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final SegmentRepository segmentRepository;
    private final ObjectMapper objectMapper;

    /**
     * 주어진 (projectId, categoryId)에 매칭되는 활성 EVENT segment ID 목록 반환.
     * 캐시 miss 시 DB 로드 + 캐시 적재. 0건이어도 빈 배열 캐시.
     */
    public List<Long> findActiveSegmentIds(Long projectId, Long categoryId) {
        Map<Long, List<Long>> map = loadOrBuild(projectId);
        return map.getOrDefault(categoryId, Collections.emptyList());
    }

    /** segment 변경/삭제/빌드 완료 시 캐시 무효화. */
    public void invalidate(Long projectId) {
        redisTemplate.delete(key(projectId));
    }

    private Map<Long, List<Long>> loadOrBuild(Long projectId) {
        String key = key(projectId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, MAP_TYPE);
            } catch (JsonProcessingException e) {
                log.warn("Active segment cache deserialize 실패 — 무효화 후 재로딩 (projectId={})", projectId, e);
                redisTemplate.delete(key);
            }
        }

        Map<Long, List<Long>> built = buildFromDb(projectId);
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(built), Duration.ofSeconds(TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.warn("Active segment cache serialize 실패 — 캐시 미적재 (projectId={})", projectId, e);
        }
        return built;
    }

    private Map<Long, List<Long>> buildFromDb(Long projectId) {
        List<Segment> segments =
                segmentRepository.findByProjectIdAndBuildStatus(projectId, BuildStatus.READY);
        Map<Long, List<Long>> bucketed = new HashMap<>();
        for (Segment s : segments) {
            Long categoryId = extractEventOnlyCategoryId(s.getConditionTree());
            if (categoryId != null) {
                bucketed.computeIfAbsent(categoryId, k -> new ArrayList<>()).add(s.getId());
            }
        }
        return bucketed;
    }

    /**
     * EVENT-only 트리(단일 root EventNode)일 때 categoryId 반환. 아니면 null.
     */
    static Long extractEventOnlyCategoryId(GroupNode tree) {
        if (tree == null || tree.conditions() == null || tree.conditions().size() != 1) {
            return null;
        }
        ConditionNode child = tree.conditions().get(0);
        if (child instanceof EventNode e) {
            return e.categoryId();
        }
        return null;
    }

    private static String key(Long projectId) {
        return KEY_PREFIX + projectId;
    }
}
