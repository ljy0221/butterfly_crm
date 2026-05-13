package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.segment.constant.AuditAction;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.dto.response.RebuildResponse;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.messaging.SegmentRebuildAcceptedEvent;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentRebuildServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private ConditionTreeHasher conditionTreeHasher;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private SegmentRebuildService segmentRebuildService;

    private static final Long PROJECT_ID = 1L;
    private static final Long SEGMENT_ID = 42L;
    private static final Long MEMBER_ID = 17L;
    private static final String STUB_HASH = "abcd1234efgh5678";

    @Test
    @DisplayName("정상 요청 — Kafka publish + audit 발행 + RebuildResponse 반환")
    void happyPath() {
        Segment seg = sampleSegment(BuildStatus.PENDING, null);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn(STUB_HASH);

        RebuildResponse response =
                segmentRebuildService.requestRebuild(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        // SegmentRebuildAcceptedEvent 발행 검증 (AFTER_COMMIT listener 가 Kafka publish).
        ArgumentCaptor<SegmentRebuildAcceptedEvent> acceptedCaptor =
                ArgumentCaptor.forClass(SegmentRebuildAcceptedEvent.class);
        verify(eventPublisher).publishEvent(acceptedCaptor.capture());
        SegmentRebuildAcceptedEvent accepted = acceptedCaptor.getValue();
        assertThat(accepted.segmentId()).isEqualTo(SEGMENT_ID);
        assertThat(accepted.projectId()).isEqualTo(PROJECT_ID);
        assertThat(accepted.memberId()).isEqualTo(MEMBER_ID);
        assertThat(accepted.requestedAt()).isNotNull();
        assertThat(accepted.conditionTreeHash()).isEqualTo(STUB_HASH);

        // audit 발행 검증
        ArgumentCaptor<AuditRecordedEvent> auditCaptor =
                ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(auditCaptor.capture());
        AuditRecordedEvent audit = auditCaptor.getValue();
        assertThat(audit.action()).isEqualTo(AuditAction.SEGMENT_REBUILD_REQUESTED);
        assertThat(audit.targetType()).isEqualTo("Segment");
        assertThat(audit.targetId()).isEqualTo(SEGMENT_ID);
        assertThat(audit.actorId()).isEqualTo(MEMBER_ID);
        assertThat(audit.projectId()).isEqualTo(PROJECT_ID);
        assertThat(audit.detail()).containsEntry("conditionTreeHash", STUB_HASH);

        // 응답 검증
        assertThat(response.segmentId()).isEqualTo(SEGMENT_ID);
        assertThat(response.buildStatus()).isEqualTo(BuildStatus.PENDING); // 현재 상태 그대로
    }

    @Test
    @DisplayName("미존재 segment — SEGMENT_404_NOT_FOUND + Kafka/audit 미발행")
    void segmentNotFound() {
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                segmentRebuildService.requestRebuild(
                                        PROJECT_ID, SEGMENT_ID, MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));

        verify(eventPublisher, never()).publishEvent(any(SegmentRebuildAcceptedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
    }

    @Test
    @DisplayName("BUILDING + heartbeat 신선 — SEGMENT_409_BUILD_IN_PROGRESS + Kafka/audit 미발행")
    void rejectsFreshBuilding() {
        Segment seg = sampleSegment(BuildStatus.BUILDING, LocalDateTime.now().minusSeconds(30));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));

        assertThatThrownBy(
                        () ->
                                segmentRebuildService.requestRebuild(
                                        PROJECT_ID, SEGMENT_ID, MEMBER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(
                                                SegmentErrorCode
                                                        .SEGMENT_409_BUILD_IN_PROGRESS));

        verify(eventPublisher, never()).publishEvent(any(SegmentRebuildAcceptedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
    }

    @Test
    @DisplayName("BUILDING + heartbeat stale — 새 rebuild 허용 (Kafka publish + audit 발행)")
    void allowsRebuildWhenStale() {
        Segment seg =
                sampleSegment(BuildStatus.BUILDING, LocalDateTime.now().minusHours(2));
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn(STUB_HASH);

        segmentRebuildService.requestRebuild(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        verify(eventPublisher).publishEvent(any(SegmentRebuildAcceptedEvent.class));
        verify(eventPublisher).publishEvent(any(AuditRecordedEvent.class));
    }

    @Test
    @DisplayName("BUILDING + heartbeat null — 잔재로 간주, rebuild 허용")
    void allowsRebuildWhenHeartbeatNull() {
        Segment seg = sampleSegment(BuildStatus.BUILDING, null);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(seg));
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn(STUB_HASH);

        segmentRebuildService.requestRebuild(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        verify(eventPublisher).publishEvent(any(SegmentRebuildAcceptedEvent.class));
    }

    @Test
    @DisplayName("READY/FAILED 상태 — rebuild 허용")
    void allowsRebuildFromReadyOrFailed() {
        Segment ready = sampleSegment(BuildStatus.READY, null);
        when(segmentRepository.findById(SEGMENT_ID)).thenReturn(Optional.of(ready));
        when(conditionTreeHasher.hash(anyLong(), any())).thenReturn(STUB_HASH);

        segmentRebuildService.requestRebuild(PROJECT_ID, SEGMENT_ID, MEMBER_ID);

        verify(eventPublisher).publishEvent(any(SegmentRebuildAcceptedEvent.class));
    }

    private Segment sampleSegment(BuildStatus status, LocalDateTime heartbeatAt) {
        GroupNode tree =
                new GroupNode(
                        LogicOp.AND,
                        List.of(
                                new TagNode(
                                        "region",
                                        Operator.EQ,
                                        JsonNodeFactory.instance.textNode("서울"))));
        Segment segment =
                Segment.builder()
                        .projectId(PROJECT_ID)
                        .name("VIP")
                        .description("desc")
                        .conditionTree(tree)
                        .createdBy(MEMBER_ID)
                        .build();
        ReflectionTestUtils.setField(segment, "id", SEGMENT_ID);
        ReflectionTestUtils.setField(segment, "buildStatus", status);
        ReflectionTestUtils.setField(segment, "buildHeartbeatAt", heartbeatAt);
        return segment;
    }
}
