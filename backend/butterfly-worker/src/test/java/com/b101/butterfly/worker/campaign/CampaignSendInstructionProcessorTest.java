package com.b101.butterfly.worker.campaign;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.port.UserContactInfo;
import com.b101.butterfly.notification.port.UserContactPort;
import com.b101.butterfly.segment.port.SegmentMemberLookupPort;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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

    private CampaignSendInstructionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CampaignSendInstructionProcessor(
                messageTemplateLookupPort,
                segmentMemberLookupPort,
                userContactPort,
                deliveryCommandKafkaTemplate
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

        List<Long> userIds = List.of(1L, 2L, 3L);
        given(segmentMemberLookupPort.getActiveUserIds(10L)).willReturn(userIds);

        List<UserContactInfo> contacts = List.of(
                new UserContactInfo(1L, 101L, "token1", "user1@test.com", "010-1111-1111"),
                new UserContactInfo(2L, 102L, "token2", "user2@test.com", "010-2222-2222"),
                new UserContactInfo(3L, 103L, "token3", "user3@test.com", "010-3333-3333")
        );
        given(userContactPort.getContactsByUserIds(100L, userIds, DeliveryCommand.Channel.PUSH))
                .willReturn(contacts);

        // when
        processor.process(instruction);

        // then
        verify(deliveryCommandKafkaTemplate).send(
                eq("delivery-requests"),
                eq("100"),  // projectId as partition key
                any(DeliveryCommand.class)
        );
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
        given(segmentMemberLookupPort.getActiveUserIds(10L)).willReturn(Collections.emptyList());

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
