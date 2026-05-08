package com.b101.butterfly.config.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.lifecycle.port.SegmentBuildStatusView;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.api.SegmentLookupPort;
import com.b101.butterfly.segment.api.SegmentSnapshot;
import com.b101.butterfly.segment.entity.BuildStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentReadinessAdapter — campaign BC ACL, segment 상태 검증")
class SegmentReadinessAdapterTest {

    private static final Long SEGMENT_ID = 10L;
    private static final Long PROJECT_ID = 1L;

    private SegmentLookupPort segmentLookupPort;
    private SegmentReadinessAdapter adapter;

    @BeforeEach
    void setUp() {
        segmentLookupPort = mock(SegmentLookupPort.class);
        adapter = new SegmentReadinessAdapter(segmentLookupPort);
    }

    private SegmentSnapshot snapshotWith(BuildStatus status) {
        return new SegmentSnapshot(
                SEGMENT_ID, PROJECT_ID, "테스트 세그먼트", "설명",
                status, 100, LocalDateTime.now(), LocalDateTime.now(),
                1L, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("requireReady(segmentId)")
    class RequireReady {

        @Test
        @DisplayName("buildStatus=READY — 예외 없이 정상 반환")
        void ready_passes() {
            given(segmentLookupPort.findById(SEGMENT_ID))
                    .willReturn(Optional.of(snapshotWith(BuildStatus.READY)));

            // 예외 없이 완료되어야 한다
            adapter.requireReady(SEGMENT_ID);
        }

        @Test
        @DisplayName("buildStatus=BUILDING — SEGMENT_NOT_READY 예외")
        void building_throws_not_ready() {
            given(segmentLookupPort.findById(SEGMENT_ID))
                    .willReturn(Optional.of(snapshotWith(BuildStatus.BUILDING)));

            assertThatThrownBy(() -> adapter.requireReady(SEGMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.SEGMENT_NOT_READY);
        }

        @Test
        @DisplayName("buildStatus=FAILED — SEGMENT_NOT_READY 예외")
        void failed_throws_not_ready() {
            given(segmentLookupPort.findById(SEGMENT_ID))
                    .willReturn(Optional.of(snapshotWith(BuildStatus.FAILED)));

            assertThatThrownBy(() -> adapter.requireReady(SEGMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.SEGMENT_NOT_READY);
        }

        @Test
        @DisplayName("buildStatus=PENDING — SEGMENT_NOT_READY 예외")
        void pending_throws_not_ready() {
            given(segmentLookupPort.findById(SEGMENT_ID))
                    .willReturn(Optional.of(snapshotWith(BuildStatus.PENDING)));

            assertThatThrownBy(() -> adapter.requireReady(SEGMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.SEGMENT_NOT_READY);
        }

        @Test
        @DisplayName("segment 미존재 — SEGMENT_NOT_FOUND 예외")
        void not_found_throws() {
            given(segmentLookupPort.findById(SEGMENT_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> adapter.requireReady(SEGMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.SEGMENT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("findStatus(segmentId)")
    class FindStatus {

        @Test
        @DisplayName("READY snapshot — SegmentBuildStatusView 반환")
        void ready_snapshot_returned() {
            given(segmentLookupPort.findById(SEGMENT_ID))
                    .willReturn(Optional.of(snapshotWith(BuildStatus.READY)));

            Optional<SegmentBuildStatusView> result = adapter.findStatus(SEGMENT_ID);

            assertThat(result).isPresent();
            assertThat(result.get().segmentId()).isEqualTo(SEGMENT_ID);
            assertThat(result.get().buildStatus()).isEqualTo("READY");
        }

        @Test
        @DisplayName("segment 미존재 — Optional.empty()")
        void missing_returns_empty() {
            given(segmentLookupPort.findById(SEGMENT_ID))
                    .willReturn(Optional.empty());

            assertThat(adapter.findStatus(SEGMENT_ID)).isEmpty();
        }
    }
}
