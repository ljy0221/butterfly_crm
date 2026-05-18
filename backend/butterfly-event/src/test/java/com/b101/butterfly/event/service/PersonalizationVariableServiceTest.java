package com.b101.butterfly.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.b101.butterfly.event.dto.response.RecommendedVariableResponse;
import com.b101.butterfly.event.repository.UserTagRepository;
import com.b101.butterfly.segment.entity.SegmentVariableSourceType;
import com.b101.butterfly.segment.entity.SegmentVariableStats;
import com.b101.butterfly.segment.entity.SegmentVariableStatus;
import com.b101.butterfly.segment.repository.SegmentVariableStatsRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalizationVariableServiceTest {

    private static final Long PROJECT_ID = 10L;
    private static final Long SEGMENT_ID = 20L;

    @Mock private UserTagRepository userTagRepository;
    @Mock private SegmentVariableStatsRepository segmentVariableStatsRepository;

    private PersonalizationVariableService service;

    @BeforeEach
    void setUp() {
        service = new PersonalizationVariableService(userTagRepository, segmentVariableStatsRepository);
    }

    @Test
    @DisplayName("segment stats가 있으면 추천 변수 응답은 segment_variable_stats를 우선 사용한다")
    void usesSegmentStatsWhenPresent() {
        given(segmentVariableStatsRepository.findByProjectIdAndSegmentIdOrderByVariableKeyAsc(
                        PROJECT_ID, SEGMENT_ID))
                .willReturn(List.of(stats("coupon_code", SegmentVariableStatus.PARTIAL, 998L, 2L)));

        RecommendedVariableResponse response = service.getRecommended(PROJECT_ID, SEGMENT_ID);

        assertThat(response.variables()).hasSize(1);
        RecommendedVariableResponse.Item item = response.variables().get(0);
        assertThat(item.key()).isEqualTo("coupon_code");
        assertThat(item.userCount()).isEqualTo(998L);
        assertThat(item.source()).isEqualTo("COUPON");
        assertThat(item.status()).isEqualTo("PARTIAL");
        assertThat(item.availableCount()).isEqualTo(998L);
        assertThat(item.missingCount()).isEqualTo(2L);
        assertThat(item.insertToken()).isEqualTo("{{coupon_code}}");
        verifyNoInteractions(userTagRepository);
    }

    @Test
    @DisplayName("segment stats가 없으면 기존 user_tags 추천으로 fallback한다")
    void fallsBackToUserTagsWhenStatsMissing() {
        given(segmentVariableStatsRepository.findByProjectIdAndSegmentIdOrderByVariableKeyAsc(
                        PROJECT_ID, SEGMENT_ID))
                .willReturn(List.of());
        given(userTagRepository.findRecommendedTagKeys(PROJECT_ID, SEGMENT_ID, 10))
                .willReturn(List.of(tag("name", 100L)));

        RecommendedVariableResponse response = service.getRecommended(PROJECT_ID, SEGMENT_ID);

        assertThat(response.variables()).hasSize(1);
        RecommendedVariableResponse.Item item = response.variables().get(0);
        assertThat(item.key()).isEqualTo("name");
        assertThat(item.source()).isEqualTo("USER_TAG");
        assertThat(item.status()).isEqualTo("READY");
        assertThat(item.availableCount()).isEqualTo(100L);
        assertThat(item.missingCount()).isZero();
        verify(userTagRepository).findRecommendedTagKeys(PROJECT_ID, SEGMENT_ID, 10);
    }

    @Test
    @DisplayName("segmentId가 없으면 project-level user_tags 추천만 조회한다")
    void projectLevelUsesUserTagsOnly() {
        given(userTagRepository.findRecommendedTagKeys(PROJECT_ID, null, 10))
                .willReturn(List.of(tag("grade", 50L)));

        RecommendedVariableResponse response = service.getRecommended(PROJECT_ID, null);

        assertThat(response.variables()).extracting(RecommendedVariableResponse.Item::key)
                .containsExactly("grade");
        verifyNoInteractions(segmentVariableStatsRepository);
    }

    private static SegmentVariableStats stats(
            String key, SegmentVariableStatus status, long availableCount, long missingCount) {
        return SegmentVariableStats.builder()
                .projectId(PROJECT_ID)
                .segmentId(SEGMENT_ID)
                .variableKey(key)
                .sourceType(SegmentVariableSourceType.COUPON)
                .availableCount(availableCount)
                .missingCount(missingCount)
                .status(status)
                .updatedAt(LocalDateTime.of(2026, 5, 18, 10, 0))
                .build();
    }

    private static UserTagRepository.RecommendedTagKey tag(String key, long userCount) {
        return new UserTagRepository.RecommendedTagKey() {
            @Override
            public String getTagKey() {
                return key;
            }

            @Override
            public long getUserCount() {
                return userCount;
            }
        };
    }
}
