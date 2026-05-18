package com.b101.butterfly.segment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.b101.butterfly.segment.entity.SegmentVariableSourceType;
import com.b101.butterfly.segment.entity.SegmentVariableStats;
import com.b101.butterfly.segment.entity.SegmentVariableStatus;
import com.b101.butterfly.testsupport.containers.AbstractMySqlContainerTest;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SegmentVariableStatsRepositoryIntegrationTest extends AbstractMySqlContainerTest {

    @Autowired private SegmentVariableStatsRepository repository;

    @Test
    @DisplayName("segmentId 기준 추천 변수 key catalog를 variableKey 순서로 조회한다")
    void findBySegmentIdOrderByVariableKeyAsc() {
        repository.save(stats(10L, 1L, "grade", SegmentVariableStatus.READY));
        repository.save(stats(10L, 1L, "coupon_code", SegmentVariableStatus.PARTIAL));
        repository.save(stats(20L, 1L, "name", SegmentVariableStatus.READY));
        repository.save(stats(10L, 2L, "name", SegmentVariableStatus.READY));

        assertThat(repository.findByProjectIdAndSegmentIdOrderByVariableKeyAsc(10L, 1L))
                .extracting(SegmentVariableStats::getVariableKey)
                .containsExactly("coupon_code", "grade");
    }

    private static SegmentVariableStats stats(
            Long projectId, Long segmentId, String key, SegmentVariableStatus status) {
        return SegmentVariableStats.builder()
                .projectId(projectId)
                .segmentId(segmentId)
                .variableKey(key)
                .sourceType(SegmentVariableSourceType.USER_TAG)
                .availableCount(10)
                .missingCount(status == SegmentVariableStatus.PARTIAL ? 1 : 0)
                .status(status)
                .sampleValue("sample")
                .updatedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
    }
}
