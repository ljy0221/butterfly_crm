package com.b101.butterfly.segment.adapter.in;

import com.b101.butterfly.segment.api.SegmentLookupPort;
import com.b101.butterfly.segment.api.SegmentSnapshot;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.support.AbstractMySqlContainerTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SegmentLookupAdapter.class)
class SegmentLookupAdapterIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentLookupPort segmentLookupPort;
    @Autowired private SegmentRepository segmentRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("findById — 존재하는 segment를 SegmentSnapshot으로 반환")
    void findByIdReturnsSnapshot() {
        Segment saved = segmentRepository.save(newSegment(1L, "VIP"));

        Optional<SegmentSnapshot> result = segmentLookupPort.findById(saved.getId());

        assertThat(result).isPresent();
        SegmentSnapshot snapshot = result.orElseThrow();
        assertThat(snapshot.segmentId()).isEqualTo(saved.getId());
        assertThat(snapshot.name()).isEqualTo("VIP");
        assertThat(snapshot.projectId()).isEqualTo(1L);
        assertThat(snapshot.buildStatus()).isEqualTo(saved.getBuildStatus());
    }

    @Test
    @DisplayName("findById — 미존재 ID는 Optional.empty()")
    void findByIdMissingReturnsEmpty() {
        assertThat(segmentLookupPort.findById(99999L)).isEmpty();
    }

    @Test
    @DisplayName("findById — soft-deleted segment는 SQLRestriction에 의해 제외")
    void findByIdSoftDeletedReturnsEmpty() {
        Segment saved = segmentRepository.save(newSegment(1L, "삭제 대상"));
        saved.softDelete();
        segmentRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(segmentLookupPort.findById(saved.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAllByIds — 다건 조회 시 모든 SegmentSnapshot 반환 (N+1 방지)")
    void findAllByIdsReturnsAll() {
        Segment a = segmentRepository.save(newSegment(1L, "A"));
        Segment b = segmentRepository.save(newSegment(1L, "B"));
        Segment c = segmentRepository.save(newSegment(2L, "C"));

        List<SegmentSnapshot> result =
                segmentLookupPort.findAllByIds(List.of(a.getId(), b.getId(), c.getId()));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(SegmentSnapshot::name).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    @DisplayName("findAllByIds — 빈 컬렉션은 빈 리스트")
    void findAllByIdsEmptyReturnsEmpty() {
        assertThat(segmentLookupPort.findAllByIds(List.of())).isEmpty();
        assertThat(segmentLookupPort.findAllByIds(null)).isEmpty();
    }

    @Test
    @DisplayName("findAllByIds — 일부만 존재하면 존재하는 것만 반환")
    void findAllByIdsPartiallyMissing() {
        Segment a = segmentRepository.save(newSegment(1L, "A"));

        List<SegmentSnapshot> result = segmentLookupPort.findAllByIds(List.of(a.getId(), 99999L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("A");
    }

    private static Segment newSegment(long projectId, String name) {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("서울"))));
        return Segment.builder()
                .projectId(projectId)
                .name(name)
                .description("desc")
                .conditionTree(tree)
                .createdBy(100L)
                .build();
    }
}
