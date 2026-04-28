package com.b101.butterfly.segment.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SegmentServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentFavoriteRepository segmentFavoriteRepository;
    @Mock private ConditionTreeValidator conditionTreeValidator;
    @Mock private LinkedCampaignChecker linkedCampaignChecker;

    @InjectMocks private SegmentService segmentService;

    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 17L;

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("정상 요청은 저장 후 응답을 반환한다")
        void happyPath() {
            CreateSegmentRequest request = new CreateSegmentRequest("VIP", "설명", validTree());
            when(segmentRepository.existsByProjectIdAndName(PROJECT_ID, "VIP")).thenReturn(false);
            when(segmentRepository.save(any(Segment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateSegmentResponse response = segmentService.create(PROJECT_ID, MEMBER_ID, request);

            assertThat(response.name()).isEqualTo("VIP");
            assertThat(response.buildStatus()).isEqualTo(BuildStatus.PENDING);
            assertThat(response.schemaVersion()).isEqualTo(3);
            assertThat(response.createdBy()).isEqualTo(MEMBER_ID);
            verify(conditionTreeValidator).validate(request.conditionTree());
            verify(segmentRepository).save(any(Segment.class));
        }

        @Test
        @DisplayName("이름 중복 시 SEGMENT_400_NAME_DUPLICATED")
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
        }

        @Test
        @DisplayName("conditionTreeValidator가 던진 BusinessException은 그대로 통과한다")
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
        @DisplayName("이름·트리 변경 후 상세 응답 반환")
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
        }

        @Test
        @DisplayName("진행 중 캠페인 연결 시 SEGMENT_409_LINKED_ACTIVE_CAMPAIGN")
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
        }

        @Test
        @DisplayName("다른 세그먼트와 이름 중복 시 SEGMENT_400_NAME_DUPLICATED (자기 자신 제외)")
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
        }

        @Test
        @DisplayName("이름이 같으면 중복 체크를 스킵하고 통과한다")
        void sameNameSkipsDuplicateCheck() {
            Segment seg = sampleSegment(42L, "same");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(false);

            UpdateSegmentRequest request =
                    new UpdateSegmentRequest("same", "new desc", validTree());

            segmentService.update(PROJECT_ID, 42L, MEMBER_ID, request);

            verify(segmentRepository, org.mockito.Mockito.never())
                    .existsByProjectIdAndName(anyLong(), eq("same"));
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("soft delete 정상")
        void happyPath() {
            Segment seg = sampleSegment(42L, "old");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(false);

            segmentService.delete(PROJECT_ID, 42L);

            assertThat(seg.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("진행 중 캠페인 연결 시 거절")
        void linkedActiveCampaign() {
            Segment seg = sampleSegment(42L, "old");
            when(segmentRepository.findById(42L)).thenReturn(Optional.of(seg));
            when(linkedCampaignChecker.hasActiveOrSending(42L)).thenReturn(true);

            assertThatThrownBy(() -> segmentService.delete(PROJECT_ID, 42L))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(
                                                    SegmentErrorCode
                                                            .SEGMENT_409_LINKED_ACTIVE_CAMPAIGN));
        }

        @Test
        @DisplayName("미존재 세그먼트 삭제 시 SEGMENT_404_NOT_FOUND")
        void notFound() {
            when(segmentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> segmentService.delete(PROJECT_ID, 99L))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(SegmentErrorCode.SEGMENT_404_NOT_FOUND));
        }
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
