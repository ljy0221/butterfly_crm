package com.b101.butterfly.segment.service.match;

import com.b101.butterfly.segment.domain.condition.EventFilter;
import com.b101.butterfly.segment.domain.condition.EventNode;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveEventSegmentCacheTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SegmentRepository segmentRepository;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private ActiveEventSegmentCache cache;

    @Test
    @DisplayName("EVENT-only 트리만 분류 — mixed/non-EVENT 트리는 제외")
    void buildBucketsExcludesNonEventOnly() throws Exception {
        // setup ObjectMapper (MockitoExtension은 final 필드 주입 안 됨 — Reflection)
        ReflectionTestUtils.setField(cache, "objectMapper", objectMapper);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(segmentRepository.findByProjectIdAndBuildStatus(1L, BuildStatus.READY))
                .thenReturn(List.of(
                        eventOnly(11L, 100L),
                        eventOnly(12L, 100L),
                        eventOnly(13L, 200L),
                        mixedTree(14L, 100L),
                        tagOnly(15L)));

        List<Long> ids = cache.findActiveSegmentIds(1L, 100L);

        assertThat(ids).containsExactlyInAnyOrder(11L, 12L);
        verify(valueOps).set(eq("seg:active:1"), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("캐시 hit 시 DB 미조회")
    void cacheHitSkipsDb() throws Exception {
        ReflectionTestUtils.setField(cache, "objectMapper", objectMapper);

        Map<Long, List<Long>> map = Map.of(100L, List.of(11L, 12L));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("seg:active:1"))
                .thenReturn(objectMapper.writeValueAsString(map));

        List<Long> ids = cache.findActiveSegmentIds(1L, 100L);

        assertThat(ids).containsExactlyInAnyOrder(11L, 12L);
        verify(segmentRepository, never())
                .findByProjectIdAndBuildStatus(any(), any());
    }

    @Test
    @DisplayName("매칭 없는 카테고리는 빈 배열")
    void unmatchedCategoryReturnsEmpty() throws Exception {
        ReflectionTestUtils.setField(cache, "objectMapper", objectMapper);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);
        when(segmentRepository.findByProjectIdAndBuildStatus(1L, BuildStatus.READY))
                .thenReturn(List.of());

        List<Long> ids = cache.findActiveSegmentIds(1L, 999L);

        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("invalidate — Redis del 호출")
    void invalidateDeletes() {
        cache.invalidate(42L);
        verify(redisTemplate).delete("seg:active:42");
    }

    @Test
    @DisplayName("extractEventOnlyCategoryId — EVENT-only 트리 매칭")
    void extractsCategoryIdFromEventOnly() {
        EventNode event = new EventNode(99L, List.of(), new Period.Cumulative(), 1);
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(event));
        assertThat(ActiveEventSegmentCache.extractEventOnlyCategoryId(tree)).isEqualTo(99L);
    }

    @Test
    @DisplayName("extractEventOnlyCategoryId — mixed 트리는 null")
    void mixedTreeReturnsNull() {
        EventNode event = new EventNode(99L, List.of(), new Period.Cumulative(), 1);
        TagNode tag = new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"));
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(event, tag));
        assertThat(ActiveEventSegmentCache.extractEventOnlyCategoryId(tree)).isNull();
    }

    private static Segment eventOnly(long id, long categoryId) {
        EventNode event = new EventNode(categoryId, List.<EventFilter>of(), new Period.Cumulative(), 1);
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(event));
        Segment s = build(id, tree);
        return s;
    }

    private static Segment tagOnly(long id) {
        TagNode tag = new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"));
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(tag));
        return build(id, tree);
    }

    private static Segment mixedTree(long id, long categoryId) {
        EventNode event = new EventNode(categoryId, List.of(), new Period.Cumulative(), 1);
        TagNode tag = new TagNode("region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"));
        GroupNode tree = new GroupNode(LogicOp.AND, List.of(event, tag));
        return build(id, tree);
    }

    private static Segment build(long id, GroupNode tree) {
        Segment s = Segment.builder()
                .projectId(1L)
                .name("seg-" + id)
                .description("d")
                .conditionTree(tree)
                .createdBy(1L)
                .build();
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "buildStatus", BuildStatus.READY);
        return s;
    }
}
