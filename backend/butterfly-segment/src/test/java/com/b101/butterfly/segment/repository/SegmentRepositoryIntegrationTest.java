package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("저장 시 기본값(schemaVersion=4, buildStatus=PENDING, isDeleted=false)이 채워진다")
    void saveAppliesDefaults() {
        Segment saved = repository.save(newSegment(1L, "VIP 고객"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSchemaVersion()).isEqualTo(4);
        assertThat(saved.getBuildStatus()).isEqualTo(BuildStatus.PENDING);
        assertThat(saved.isDeleted()).isFalse();
        assertThat(saved.getEstimatedCount()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("conditionTree JSON 컬럼이 round-trip 되어 GroupNode로 복원된다")
    void conditionTreeRoundTripsThroughJsonColumn() {
        Segment saved = repository.save(newSegment(1L, "서울 고객"));
        repository.flush();

        Segment reloaded = repository.findById(saved.getId()).orElseThrow();

        GroupNode tree = reloaded.getConditionTree();
        assertThat(tree.logic()).isEqualTo(LogicOp.AND);
        assertThat(tree.conditions()).hasSize(1).first().isInstanceOf(TagNode.class);
    }

    @Test
    @DisplayName("findByProjectId는 같은 프로젝트의 세그먼트만 페이지로 반환한다")
    void findByProjectIdScopesByProject() {
        repository.save(newSegment(1L, "A"));
        repository.save(newSegment(1L, "B"));
        repository.save(newSegment(2L, "C"));

        Page<Segment> page = repository.findByProjectId(1L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Segment::getName)
                .containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("existsByProjectIdAndName으로 동일 프로젝트 내 이름 중복을 확인할 수 있다")
    void existsByProjectIdAndNameDetectsDuplicate() {
        repository.save(newSegment(1L, "VIP"));

        assertThat(repository.existsByProjectIdAndName(1L, "VIP")).isTrue();
        assertThat(repository.existsByProjectIdAndName(1L, "Other")).isFalse();
        assertThat(repository.existsByProjectIdAndName(2L, "VIP")).isFalse();
    }

    @Test
    @DisplayName("softDelete된 세그먼트는 SQLRestriction에 의해 조회에서 제외된다")
    void softDeletedSegmentIsHidden() {
        Segment saved = repository.save(newSegment(1L, "삭제 대상"));
        saved.softDelete();
        repository.saveAndFlush(saved);
        entityManager.clear();

        Optional<Segment> found = repository.findById(saved.getId());
        assertThat(found).isEmpty();

        Page<Segment> all = repository.findByProjectId(1L, PageRequest.of(0, 10));
        assertThat(all.getTotalElements()).isZero();
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
