package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentMember;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentMemberRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentMemberRepository repository;

    @Test
    @DisplayName("countBySegmentId는 같은 세그먼트의 멤버 수를 반환한다")
    void countBySegmentId() {
        repository.save(member(1L, 100L));
        repository.save(member(1L, 101L));
        repository.save(member(2L, 100L));

        assertThat(repository.countBySegmentId(1L)).isEqualTo(2);
        assertThat(repository.countBySegmentId(2L)).isEqualTo(1);
        assertThat(repository.countBySegmentId(99L)).isZero();
    }

    @Test
    @DisplayName("findBySegmentId는 같은 세그먼트의 멤버를 페이지로 반환한다")
    void findBySegmentIdScopesBySegment() {
        repository.save(member(1L, 100L));
        repository.save(member(1L, 101L));
        repository.save(member(2L, 200L));

        Page<SegmentMember> page = repository.findBySegmentId(1L, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(SegmentMember::getUserId)
                .containsExactlyInAnyOrder(100L, 101L);
    }

    @Test
    @DisplayName("expiresAt이 nullable로 저장된다 (cumulative period 시나리오)")
    void expiresAtAllowsNull() {
        SegmentMember saved =
                repository.save(SegmentMember.builder().segmentId(1L).userId(100L).build());

        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.getMatchedAt()).isNotNull();
    }

    private static SegmentMember member(long segmentId, long userId) {
        return SegmentMember.builder()
                .segmentId(segmentId)
                .userId(userId)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
    }
}
