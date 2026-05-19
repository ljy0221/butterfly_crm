package com.b101.butterfly.worker.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.event.port.UserTagLookupPort;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.port.UserContactInfo;
import com.b101.butterfly.notification.port.UserContactPort;
import com.b101.butterfly.payment.application.CreditAccountService;
import com.b101.butterfly.payment.exception.code.PaymentErrorCode;
import com.b101.butterfly.segment.port.SegmentAudienceTarget;
import com.b101.butterfly.segment.port.SegmentMemberLookupPort;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignSendInstructionProcessor")
class CampaignSendInstructionProcessorTest {

    @Mock
    private MessageTemplateLookupPort messageTemplateLookupPort;

    @Mock
    private SegmentMemberLookupPort segmentMemberLookupPort;

    @Mock
    private UserContactPort userContactPort;

    @Mock
    private KafkaTemplate<String, DeliveryCommand> deliveryCommandKafkaTemplate;

    @Mock
    private CampaignExecutionResultRecorder campaignExecutionResultRecorder;

    @Mock
    private UserTagLookupPort userTagLookupPort;

    @Mock
    private CreditAccountService creditAccountService;

    private CampaignSendInstructionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CampaignSendInstructionProcessor(
                messageTemplateLookupPort,
                segmentMemberLookupPort,
                userContactPort,
                userTagLookupPort,
                deliveryCommandKafkaTemplate,
                campaignExecutionResultRecorder,
                creditAccountService
        );
    }

    @Test
    @DisplayName("정상 처리 시 DeliveryCommand를 delivery-requests 토픽으로 발행")
    void processSuccessfully() {
        // given
        CampaignSendInstruction instruction = createSampleInstruction();

        MessageTemplateSnapshot template = new MessageTemplateSnapshot(
                1L, 100L, Channel.PUSH,
                "테스트 제목", "테스트 본문", "https://image.url"
        );
        given(messageTemplateLookupPort.lookup(1L)).willReturn(template);

        List<SegmentAudienceTarget> targets = List.of(
                new SegmentAudienceTarget(1L, Map.of("name", "Alice")),
                new SegmentAudienceTarget(2L, Map.of("name", "Bob")),
                new SegmentAudienceTarget(3L, Map.of("name", "Carol"))
        );
        List<Long> userIds = List.of(1L, 2L, 3L);
        given(segmentMemberLookupPort.getActiveTargets(10L, null, Integer.MAX_VALUE))
                .willReturn(targets);

        List<UserContactInfo> contacts = List.of(
                new UserContactInfo(1L, 101L, "token1", "user1@test.com", "010-1111-1111", null, null),
                new UserContactInfo(2L, 102L, "token2", "user2@test.com", "010-2222-2222", null, null),
                new UserContactInfo(3L, 103L, "token3", "user3@test.com", "010-3333-3333", null, null)
        );
        given(userContactPort.getContactsByUserIds(100L, userIds, DeliveryCommand.Channel.PUSH))
                .willReturn(contacts);

        // when
        processor.process(instruction);

        // then
        ArgumentCaptor<DeliveryCommand> commandCaptor =
                ArgumentCaptor.forClass(DeliveryCommand.class);
        verify(deliveryCommandKafkaTemplate).send(
                eq("delivery-requests"),
                eq("100"),  // projectId as partition key
                commandCaptor.capture()
        );
        assertThat(commandCaptor.getValue().targets().get(0).variables())
                .containsEntry("name", "Alice");
        verify(creditAccountService).reserveForCampaignExecution(100L, 123L, 999L, 3, 1L);
    }

    @Test
    @DisplayName("세그먼트 snapshot 변수가 비어 있으면 기존 user_tags 경로로 fallback")
    void fallsBackToUserTagsWhenSnapshotVariablesAreEmpty() {
        CampaignSendInstruction instruction = createSampleInstruction();
        MessageTemplateSnapshot template = new MessageTemplateSnapshot(
                1L, 100L, Channel.PUSH,
                "테스트 제목", "안녕하세요 {{name}}님", null
        );
        given(messageTemplateLookupPort.lookup(1L)).willReturn(template);
        given(segmentMemberLookupPort.getActiveTargets(10L, null, Integer.MAX_VALUE))
                .willReturn(List.of(new SegmentAudienceTarget(1L, Map.of())));
        List<UserContactInfo> contacts = List.of(
                new UserContactInfo(1L, 101L, "token1", "user1@test.com", "010-1111-1111", null, null)
        );
        given(userContactPort.getContactsByUserIds(
                100L, List.of(1L), DeliveryCommand.Channel.PUSH))
                .willReturn(contacts);
        given(userTagLookupPort.getTagsByUserIds(100L, List.of(1L)))
                .willReturn(Map.of(1L, Map.of("name", "FallbackAlice")));

        processor.process(instruction);

        ArgumentCaptor<DeliveryCommand> commandCaptor =
                ArgumentCaptor.forClass(DeliveryCommand.class);
        verify(deliveryCommandKafkaTemplate).send(
                eq("delivery-requests"),
                eq("100"),
                commandCaptor.capture());
        assertThat(commandCaptor.getValue().targets().get(0).variables())
                .containsEntry("name", "FallbackAlice");
        verify(creditAccountService).reserveForCampaignExecution(100L, 123L, 999L, 1, 1L);
    }

    @Test
    @DisplayName("크레딧이 부족하면 DeliveryCommand를 발행하지 않고 실행 실패 처리")
    void skipWhenCreditIsInsufficient() {
        // given
        CampaignSendInstruction instruction = createSampleInstruction();

        MessageTemplateSnapshot template = new MessageTemplateSnapshot(
                1L, 100L, Channel.PUSH,
                "테스트 제목", "테스트 본문", null
        );
        given(messageTemplateLookupPort.lookup(1L)).willReturn(template);

        List<SegmentAudienceTarget> targets = List.of(
                new SegmentAudienceTarget(1L, Map.of("name", "Alice")),
                new SegmentAudienceTarget(2L, Map.of("name", "Bob")),
                new SegmentAudienceTarget(3L, Map.of("name", "Carol"))
        );
        List<Long> userIds = List.of(1L, 2L, 3L);
        given(segmentMemberLookupPort.getActiveTargets(10L, null, Integer.MAX_VALUE))
                .willReturn(targets);

        List<UserContactInfo> contacts = List.of(
                new UserContactInfo(1L, 101L, "token1", "user1@test.com", "010-1111-1111", null, null),
                new UserContactInfo(2L, 102L, "token2", "user2@test.com", "010-2222-2222", null, null),
                new UserContactInfo(3L, 103L, "token3", "user3@test.com", "010-3333-3333", null, null)
        );
        given(userContactPort.getContactsByUserIds(100L, userIds, DeliveryCommand.Channel.PUSH))
                .willReturn(contacts);
        willThrow(new BusinessException(PaymentErrorCode.PAY_INSUFFICIENT_BALANCE))
                .given(creditAccountService)
                .reserveForCampaignExecution(100L, 123L, 999L, 3, 1L);

        // when
        processor.process(instruction);

        // then
        verify(deliveryCommandKafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(campaignExecutionResultRecorder).recordFailure(999L);
    }

    @Test
    @DisplayName("세그먼트 멤버가 없으면 DeliveryCommand 발행하지 않음")
    void skipWhenNoSegmentMembers() {
        // given
        CampaignSendInstruction instruction = createSampleInstruction();

        MessageTemplateSnapshot template = new MessageTemplateSnapshot(
                1L, 100L, Channel.PUSH,
                "테스트 제목", "테스트 본문", null
        );
        given(messageTemplateLookupPort.lookup(1L)).willReturn(template);
        given(segmentMemberLookupPort.getActiveTargets(10L, null, Integer.MAX_VALUE))
                .willReturn(Collections.emptyList());

        // when
        processor.process(instruction);

        // then
        verify(deliveryCommandKafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    private CampaignSendInstruction createSampleInstruction() {
        return CampaignSendInstruction.of(
                999L,   // executionId
                123L,   // campaignId
                100L,   // projectId
                1L,     // templateId
                10L,    // segmentId
                Channel.PUSH,
                Instant.now()
        );
    }
}
