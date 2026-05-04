package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.segment.constant.AuditAction;
import com.b101.butterfly.segment.domain.condition.GroupNode;
import com.b101.butterfly.segment.domain.condition.LogicOp;
import com.b101.butterfly.segment.domain.condition.Operator;
import com.b101.butterfly.segment.domain.condition.TagNode;
import com.b101.butterfly.segment.domain.condition.validation.ConditionTreeValidator;
import com.b101.butterfly.segment.dto.request.CreateSegmentRequest;
import com.b101.butterfly.segment.dto.request.UpdateSegmentRequest;
import com.b101.butterfly.segment.dto.response.CreateSegmentResponse;
import com.b101.butterfly.segment.dto.response.SegmentDetailResponse;
import com.b101.butterfly.segment.entity.BuildStatus;
import com.b101.butterfly.segment.entity.Segment;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import com.b101.butterfly.segment.port.LinkedCampaignChecker;
import com.b101.butterfly.segment.port.LinkedCampaignsView;
import com.b101.butterfly.segment.repository.SegmentFavoriteRepository;
import com.b101.butterfly.segment.repository.SegmentRepository;
import com.b101.butterfly.segment.service.preview.ConditionTreeHasher;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentFavoriteRepository segmentFavoriteRepository;
    @Mock private ConditionTreeValidator conditionTreeValidator;
    @Mock private LinkedCampaignChecker linkedCampaignChecker;
    @Mock private ConditionTreeHasher conditionTreeHasher;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private SegmentService segmentService;

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 17L;
    private static final String STUB_HASH =
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2";

    @BeforeEach
    void hasherStub() {
        // 모든 케이스에서 같은 hash를 반환 — 본 단위 테스트는 audit 발행 여부·핵심 필드 검증이 목적이고
        // hash 동일성에 의존하는 케이스(no-op update)는 별도로 다룬다.
        lenient().when(conditionTreeHasher.hash(anyLong(), any())).thenReturn(STUB_HASH);
    }

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("정상 요청은 저장 후 응답 반환 + SEGMENT_CREATED audit 발행")
        void happyPath() {
            CreateSegmentRequest request = new CreateSegmentRequest("VIP", "설명", validTree());
            when(segmentRepository.existsByProjectIdAndName(PROJECT_ID, "VIP")).thenReturn(false);
            when(segmentRepository.save(any(Segment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateSegmentResponse response = segmentService.create(PROJECT_ID, MEMBER_ID, request);

            assertThat(response.name()).isEqualTo("VIP");
            assertThat(response.buildStatus()).isEqualTo(BuildStatus.PENDING);
            assertThat(response.schemaVersion()).isEqualTo(4);
            assertThat(response.createdBy()).isEqualTo(MEMBER_ID);
            verify(conditionTreeValidator).validate(request.conditionTree());
            verify(segmentRepository).save(any(Segment.class));

            AuditRecordedEvent event = captureAuditEvent();
            assertThat(event.action()).isEqualTo(AuditAction.SEGMENT_CREATED);
            assertThat(event.targetType()).isEqualTo("Segment");
            assertThat(event.actorId()).isEqualTo(MEMBER_ID);
            assertThat(event.projectId()).isEqualTo(PROJECT_ID);
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
            assertThat(event.detail())
                    .containsEntry("name", "VIP")
                    .containsEntry("conditionTreeHash", STUB_HASH.substring(0, 8))
                    .containsEntry("conditionTreeVersion", 4);
        }

        @Test
        @DisplayName("이름 중복 시 SEGMENT_400_NAME_DUPLICATED + audit 미발행")
        void nameDuplicated() {
            CreateSegmentRequest request = new CreateSegmentRequest("VIP", "설명", validTree());
            when(segmentRepository.existsByProjectIdAndName(PROJECT_ID, "VIP")).thenReturn(true);

            assertThatThrownBy(() -> segmentService.create(PROJECT_ID, MEMBER_ID, request))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(
                                                    SegmentErrorCode
                                                            .SEGMENT_400_NAME_DUPLICATED));

            verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
        }

        @Test
        @DisplayName("conditionTreeValidator가 던진 BusinessException은 그대로 통과 + audit 미발행")
        void conditionTreeValidationFails() {
            CreateSegmentRequest request = new CreateSegmentRequest("VIP", "설명", validTree());
            when(segmentRepository.existsByProjectIdAndName(PROJECT_ID, "VIP")).thenReturn(false);
            org.mockito.Mockito.doThrow(
                            new BusinessException(SegmentErrorCode.SEGMENT_400_EMPTY_CONDITIONS))
                    .when(conditionTreeValidator)
                    .validate(any());

            assertThatThrownBy(() -> segmentService.create(PROJECT_ID, MEMBER_ID, request))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(
                                                    SegmentErrorCode
                                                            .SEGMENT_400_EMPTY_CONDITIONS));

            verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
        }
    }

    @Nested
    @DisplayName("list()")
    class ListTests {

        @Test
        @DisplayName("페이지를 SegmentSummary로 변환해 반환한다")
        void happyPath() {
            Segment seg = sampleSegment(42L, "VIP");
            Page<Segment> page = new PageImpl<>(List.of(seg), PageRequest.of(0, 20), 1);
            when(segmentRepository.findByProjectId(eq(PROJECT_ID), any())).thenReturn(page);
            when(segmentFavoriteRepository.existsBySegmentIdAndMemberId(42L, MEMBER_ID))
                    .thenReturn(true);

            PageResponse<?> result =
                    segmentService.list(PROJECT_ID, MEMBER_ID, PageRequest.of(0, 20));

            assertThat(result.totalElements()).isEqualTo(1);
            assertThat(result.content()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("detail()")
    class DetailTests {

        @BeforeEach
        void setUp() {
            lenient()
                    .when(linkedCampaignChecker.listLinkedCampaigns(anyLong()))
                    .thenReturn(LinkedCampaignsView.empty());
        }

        @Test
        @DisplayName("정상 조회 시 linkedCampaigns/favorited 채워서 반환")
        void happyPath() {
            Segment seg = sampleSegment(42L, "VIP");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(segmentFavoriteRepository.existsBySegmentIdAndMemberId(42L, MEMBER_ID))
                    .thenReturn(true);

            SegmentDetailResponse response =
                    segmentService.detail(PROJECT_ID, 42L, MEMBER_ID);

            assertThat(response.segmentId()).isEqualTo(42L);
            assertThat(response.favorited()).isTrue();
            assertThat(response.linkedCampaigns()).isEmpty();
        }

        @Test
        @DisplayName("미존재 세그먼트는 SEGMENT_404_NOT_FOUND")
        void notFound() {
            when(segmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> segmentService.detail(PROJECT_ID, 99L, MEMBER_ID))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @BeforeEach
        void setUp() {
            lenient()
                    .when(linkedCampaignChecker.listLinkedCampaigns(anyLong()))
                    .thenReturn(LinkedCampaignsView.empty());
        }

        @Test
        @DisplayName("이름·설명 변경 후 상세 응답 반환 + SEGMENT_UPDATED audit 발행 (before/after)")
        void happyPath() {
            Segment seg = sampleSegment(42L, "old");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(false);
            when(segmentRepository.existsByProjectIdAndName(PROJECT_ID, "renamed"))
                    .thenReturn(false);

            UpdateSegmentRequest request =
                    new UpdateSegmentRequest("renamed", "new desc", validTree());
            SegmentDetailResponse response =
                    segmentService.update(PROJECT_ID, 42L, MEMBER_ID, request);

            assertThat(response.name()).isEqualTo("renamed");
            assertThat(response.buildStatus()).isEqualTo(BuildStatus.PENDING);
            verify(conditionTreeValidator).validate(request.conditionTree());

            AuditRecordedEvent event = captureAuditEvent();
            assertThat(event.action()).isEqualTo(AuditAction.SEGMENT_UPDATED);
            assertThat(event.targetType()).isEqualTo("Segment");
            assertThat(event.targetId()).isEqualTo(42L);
            assertThat(event.actorId()).isEqualTo(MEMBER_ID);
            assertThat(event.projectId()).isEqualTo(PROJECT_ID);
            // hasher가 같은 hash 반환하므로 conditionTreeChanged=false. name/description은 바뀜
            assertThat(event.detail()).containsEntry("conditionTreeChanged", false);
            assertThat(event.detail()).containsKeys("before", "after");
            @SuppressWarnings("unchecked")
            Map<String, Object> before = (Map<String, Object>) event.detail().get("before");
            @SuppressWarnings("unchecked")
            Map<String, Object> after = (Map<String, Object>) event.detail().get("after");
            assertThat(before).containsEntry("name", "old").containsEntry("description", "desc");
            assertThat(after).containsEntry("name", "renamed").containsEntry("description", "new desc");
        }

        @Test
        @DisplayName("진행 중 캠페인 연결 시 SEGMENT_409 + audit 미발행")
        void linkedActiveCampaign() {
            Segment seg = sampleSegment(42L, "old");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(true);

            UpdateSegmentRequest request =
                    new UpdateSegmentRequest("renamed", "new desc", validTree());

            assertThatThrownBy(
                            () -> segmentService.update(PROJECT_ID, 42L, MEMBER_ID, request))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(
                                                    SegmentErrorCode
                                                            .SEGMENT_409_LINKED_ACTIVE_CAMPAIGN));

            verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
        }

        @Test
        @DisplayName("이름 중복 시 SEGMENT_400_NAME_DUPLICATED + audit 미발행")
        void nameDuplicated() {
            Segment seg = sampleSegment(42L, "old");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(false);
            when(segmentRepository.existsByProjectIdAndName(PROJECT_ID, "renamed"))
                    .thenReturn(true);

            UpdateSegmentRequest request =
                    new UpdateSegmentRequest("renamed", "new desc", validTree());

            assertThatThrownBy(
                            () -> segmentService.update(PROJECT_ID, 42L, MEMBER_ID, request))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(
                                                    SegmentErrorCode
                                                            .SEGMENT_400_NAME_DUPLICATED));

            verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
        }

        @Test
        @DisplayName("이름이 같으면 중복 체크를 스킵하고 통과한다 (description 변경분만 audit)")
        void sameNameSkipsDuplicateCheck() {
            Segment seg = sampleSegment(42L, "same");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(false);

            UpdateSegmentRequest request =
                    new UpdateSegmentRequest("same", "new desc", validTree());

            segmentService.update(PROJECT_ID, 42L, MEMBER_ID, request);

            verify(segmentRepository, never())
                    .existsByProjectIdAndName(anyLong(), eq("same"));
            // description 변경됨 → audit 발행
            verify(eventPublisher).publishEvent(any(AuditRecordedEvent.class));
        }

        @Test
        @DisplayName("no-op 변경(이름·설명·트리 모두 동일)은 audit 발행 X")
        void noOpUpdateSkipsAudit() {
            Segment seg = sampleSegment(42L, "same");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(false);

            UpdateSegmentRequest request = new UpdateSegmentRequest("same", "desc", validTree());
            segmentService.update(PROJECT_ID, 42L, MEMBER_ID, request);

            verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("soft delete 정상 + SEGMENT_DELETED audit 발행")
        void happyPath() {
            Segment seg = sampleSegment(42L, "old");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(false);

            segmentService.delete(PROJECT_ID, 42L, MEMBER_ID);

            assertThat(seg.isDeleted()).isTrue();
            AuditRecordedEvent event = captureAuditEvent();
            assertThat(event.action()).isEqualTo(AuditAction.SEGMENT_DELETED);
            assertThat(event.targetType()).isEqualTo("Segment");
            assertThat(event.targetId()).isEqualTo(42L);
            assertThat(event.actorId()).isEqualTo(MEMBER_ID);
            assertThat(event.projectId()).isEqualTo(PROJECT_ID);
            assertThat(event.detail()).containsEntry("name", "old");
        }

        @Test
        @DisplayName("진행 중 캠페인 연결 시 거절 + audit 미발행")
        void linkedActiveCampaign() {
            Segment seg = sampleSegment(42L, "old");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(true);

            assertThatThrownBy(() -> segmentService.delete(PROJECT_ID, 42L, MEMBER_ID))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(
                                                    SegmentErrorCode
                                                            .SEGMENT_409_LINKED_ACTIVE_CAMPAIGN));
            verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
        }

        @Test
        @DisplayName("미존재 세그먼트 삭제 시 SEGMENT_404_NOT_FOUND + audit 미발행")
        void notFound() {
            when(segmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> segmentService.delete(PROJECT_ID, 99L, MEMBER_ID))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));
            verify(eventPublisher, never()).publishEvent(any(AuditRecordedEvent.class));
        }
    }

    private AuditRecordedEvent captureAuditEvent() {
        ArgumentCaptor<AuditRecordedEvent> captor = ArgumentCaptor.forClass(AuditRecordedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static GroupNode validTree() {
        return new GroupNode(
                LogicOp.AND,
                List.of(
                        new TagNode(
                                "region", Operator.EQ, JsonNodeFactory.instance.textNode("서울"))));
    }

    private static Segment sampleSegment(long id, String name) {
        Segment segment =
                Segment.builder()
                        .projectId(PROJECT_ID)
                        .name(name)
                        .description("desc")
                        .conditionTree(validTree())
                        .createdBy(MEMBER_ID)
                        .build();
        org.springframework.test.util.ReflectionTestUtils.setField(segment, "id", id);
        return segment;
    }
}
