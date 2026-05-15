package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.segment.constant.AuditAction;
import com.b101.butterfly.segment.dto.response.CancelBuildResponse;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.repository.SegmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentBuildCancelServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private SegmentBuildCancelService segmentBuildCancelService;

    private static final Long PROJECT_ID = 1L;
    private static final Long SEGMENT_ID = 42L;
    private static final Long MEMBER_ID = 17L;

    @Test
    @DisplayName("정상 취소 — cancelBuildIfPossible 1 row 매칭 → FAILED 응답 + audit 발행")
    void happyPath() {
        when(segmentRepository.cancelBuildIfPossible(eq(SEGMENT_ID), any(), any()))
                .thenReturn(1);

        CancelBuildResponse response =
                segmentBuildCancelService.cancelBuild(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        assertThat(response.segmentId()).isEqualTo(SEGMENT_ID);
        assertThat(response.buildStatus()).isEqualTo(BuildStatus.FAILED);
        assertThat(response.canceledAt()).isNotNull();

        // cancel marker 가 'cancel-' prefix 인지 확인.
        ArgumentCaptor<String> markerCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> nowCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(segmentRepository).cancelBuildIfPossible(eq(SEGMENT_ID), markerCap.capture(), nowCap.capture());
        assertThat(markerCap.getValue()).startsWith("cancel-");
        assertThat(nowCap.getValue()).isNotNull();

        // audit SEGMENT_BUILD_CANCELED 발행 확인.
        ArgumentCaptor<AuditRecordedEvent> auditCap = ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(auditCap.capture());
        AuditRecordedEvent audit = auditCap.getValue();
        assertThat(audit.action()).isEqualTo(AuditAction.SEGMENT_BUILD_CANCELED);
        assertThat(audit.projectId()).isEqualTo(PROJECT_ID);
        assertThat(audit.actorId()).isEqualTo(MEMBER_ID);
        assertThat(audit.targetType()).isEqualTo("Segment");
        assertThat(audit.targetId()).isEqualTo(SEGMENT_ID);
    }

    @Test
    @DisplayName("취소 대상 없음 (BUILDING/PENDING 아님) — SEGMENT_409_NO_CANCELLABLE_BUILD")
    void notCancellable() {
        when(segmentRepository.cancelBuildIfPossible(eq(SEGMENT_ID), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> segmentBuildCancelService.cancelBuild(PROJECT_ID, SEGMENT_ID, MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_409_NO_CANCELLABLE_BUILD));

        // audit 미발행.
        verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
    }
}
