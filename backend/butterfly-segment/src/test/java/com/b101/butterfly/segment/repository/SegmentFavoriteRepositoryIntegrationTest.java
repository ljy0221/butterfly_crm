package com.b101.butterfly.segment.repository;

import com.b101.butterfly.segment.entity.SegmentFavorite;
import com.b101.butterfly.segment.support.AbstractMySqlContainerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentFavoriteRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentFavoriteRepository repository;

    @Test
    @DisplayName("existsBySegmentIdAndMemberId — 등록 후 true, 미등록은 false")
    void existsBySegmentIdAndMemberId() {
        repository.save(favorite(1L, 100L));

        assertThat(repository.existsBySegmentIdAndMemberId(1L, 100L)).isTrue();
        assertThat(repository.existsBySegmentIdAndMemberId(1L, 101L)).isFalse();
        assertThat(repository.existsBySegmentIdAndMemberId(2L, 100L)).isFalse();
    }

    @Test
    @DisplayName("deleteBySegmentIdAndMemberId — 삭제 후 exists가 false")
    void deleteBySegmentIdAndMemberId() {
        repository.save(favorite(1L, 100L));
        repository.flush();

        repository.deleteBySegmentIdAndMemberId(1L, 100L);
        repository.flush();

        assertThat(repository.existsBySegmentIdAndMemberId(1L, 100L)).isFalse();
    }

    @Test
    @DisplayName("findByMemberId — 멤버의 모든 즐겨찾기를 반환한다")
    void findByMemberId() {
        repository.save(favorite(1L, 100L));
        repository.save(favorite(2L, 100L));
        repository.save(favorite(3L, 200L));

        List<SegmentFavorite> result = repository.findByMemberId(100L);

        assertThat(result)
                .extracting(SegmentFavorite::getSegmentId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    private static SegmentFavorite favorite(long segmentId, long memberId) {
        return SegmentFavorite.builder().segmentId(segmentId).memberId(memberId).build();
    }
}
