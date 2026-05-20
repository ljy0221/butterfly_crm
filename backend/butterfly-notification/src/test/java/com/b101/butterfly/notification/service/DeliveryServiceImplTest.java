package com.b101.butterfly.notification.service;

import com.b101.butterfly.event.port.UserTagLookupPort;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.Channel;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.DeliveryTarget;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.FcmCredential;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.MessagePayload;
import com.b101.butterfly.notification.dto.response.DeliveryResult;
import com.b101.butterfly.notification.dto.response.TargetOutcome;
import com.b101.butterfly.notification.exception.DeliveryErrorCode;
import com.b101.butterfly.notification.port.UserContactInfo;
import com.b101.butterfly.notification.port.UserContactPort;
import com.b101.butterfly.notification.service.credential.ChannelCredentialService;
import com.b101.butterfly.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    @Mock
    private ChannelAdapterRegistry channelAdapterRegistry;
    @Mock
    private DeliveryResultPersister deliveryResultPersister;
    @Mock
    private ChannelCredentialService channelCredentialService;
    @Mock
    private UserContactPort userContactPort;
    @Mock
    private UserTagLookupPort userTagLookupPort;
    @Mock
    private CampaignExecutionResultPublisher campaignExecutionResultPublisher;

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    private static final Long PROJECT_ID = 1L;
    private static final Long CAMPAIGN_ID = 100L;
    private static final Long EXECUTION_ID = 999L;
    private static final MessagePayload MESSAGE = new MessagePayload("title", "body", null, null);

    // @Test
    // @DisplayName("credential과 targets가 직접 전달되면 DB 조회 없이 바로 발송한다")
    // void directCredentialAndTargets_skipsDbLookup() {
    // FcmCredential credential = new FcmCredential("{\"type\":\"service_account\"}");
    // DeliveryTarget target = new DeliveryTarget(1L, 10L, "token-abc", null, null);
    // DeliveryCommand command = new DeliveryCommand(PROJECT_ID, CAMPAIGN_ID, EXECUTION_ID,
    // Channel.PUSH, MESSAGE, null, credential, List.of(target));

    // ChannelAdapter mockAdapter = stubAdapter();
    // when(channelAdapterRegistry.getAdapter(Channel.PUSH)).thenReturn(mockAdapter);

    // deliveryService.deliver(command);

    // verify(channelCredentialService, never()).getCredential(any(), any());
    // verify(userContactPort, never()).getContactsByUserIds(any(), any(), any());
    // verify(deliveryResultPersister).persist(any(), any());
    // }

    // @Test
    // @DisplayName("credential과 targets가 null이면 DB에서 조회하고 user_tags 로 variables 까지 채운다")
    // void nullCredentialAndTargets_fetchesFromDbWithVariables() {
    // DeliveryCommand command = new DeliveryCommand(PROJECT_ID, CAMPAIGN_ID, EXECUTION_ID,
    // Channel.PUSH, MESSAGE, List.of(1L, 2L), null, null);

    // FcmCredential credential = new FcmCredential("{\"type\":\"service_account\"}");
    // when(channelCredentialService.getCredential(PROJECT_ID, Channel.PUSH))
    // .thenReturn(credential);
    // when(userContactPort.getContactsByUserIds(PROJECT_ID, List.of(1L, 2L), Channel.PUSH))
    // .thenReturn(List.of(new UserContactInfo(1L, 10L, "token-1", null, null, null, null),
    // new UserContactInfo(2L, 20L, "token-2", null, null, null, null)));
    // when(userTagLookupPort.getTagsByUserIds(PROJECT_ID, List.of(1L, 2L)))
    // .thenReturn(Map.of(1L, Map.of("name", "Alice"), 2L, Map.of("name", "Bob")));

    // // ChannelAdapter 가 받은 targets 의 variables 를 capture
    // CapturingAdapter capturing = new CapturingAdapter();
    // when(channelAdapterRegistry.getAdapter(Channel.PUSH)).thenReturn(capturing);

    // deliveryService.deliver(command);

    // verify(channelCredentialService).getCredential(PROJECT_ID, Channel.PUSH);
    // verify(userContactPort).getContactsByUserIds(PROJECT_ID, List.of(1L, 2L), Channel.PUSH);
    // verify(userTagLookupPort).getTagsByUserIds(PROJECT_ID, List.of(1L, 2L));
    // verify(deliveryResultPersister).persist(any(DeliveryCommand.class), any());

    // // 각 target 의 variables 가 user_tags 매핑대로 채워졌는지 검증
    // assertThat(capturing.captured()).hasSize(2);
    // assertThat(capturing.captured().get(0).variables()).containsEntry("name", "Alice");
    // assertThat(capturing.captured().get(1).variables()).containsEntry("name", "Bob");
    // }

    // @Test
    // @DisplayName("userIds 경로에서 user_tags 가 비어 있어도 정상 처리 (variables=empty Map)")
    // void userIdsPath_emptyUserTags_okay() {
    // DeliveryCommand command = new DeliveryCommand(PROJECT_ID, CAMPAIGN_ID, EXECUTION_ID,
    // Channel.PUSH, MESSAGE, List.of(7L), null, null);

    // FcmCredential credential = new FcmCredential("{\"type\":\"service_account\"}");
    // when(channelCredentialService.getCredential(PROJECT_ID, Channel.PUSH))
    // .thenReturn(credential);
    // when(userContactPort.getContactsByUserIds(PROJECT_ID, List.of(7L), Channel.PUSH))
    // .thenReturn(
    // List.of(new UserContactInfo(7L, 70L, "token-7", null, null, null, null)));
    // when(userTagLookupPort.getTagsByUserIds(PROJECT_ID, List.of(7L))).thenReturn(Map.of());

    // when(channelAdapterRegistry.getAdapter(Channel.PUSH)).thenReturn(stubAdapter());

    // deliveryService.deliver(command);

    // verify(userTagLookupPort).getTagsByUserIds(PROJECT_ID, List.of(7L));
    // verify(deliveryResultPersister).persist(any(), any());
    // }

    @Test
    @DisplayName("targets가 비어 있으면 EMPTY_TARGETS 예외를 던진다")
    void emptyTargets_throwsException() {
        FcmCredential credential = new FcmCredential("{\"type\":\"service_account\"}");
        DeliveryCommand command = new DeliveryCommand(PROJECT_ID, CAMPAIGN_ID, EXECUTION_ID,
                Channel.PUSH, MESSAGE, null, credential, List.of());

        assertThatThrownBy(() -> deliveryService.deliver(command)).isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(DeliveryErrorCode.EMPTY_TARGETS));
    }

    // @Test
    // @DisplayName("발송 성공 후 persist가 호출된다")
    // void afterSend_persistIsCalled() {
    // FcmCredential credential = new FcmCredential("{\"type\":\"service_account\"}");
    // DeliveryTarget target1 = new DeliveryTarget(1L, 10L, "token-1", null, null);
    // DeliveryTarget target2 = new DeliveryTarget(2L, 20L, "token-2", null, null);
    // DeliveryCommand command = new DeliveryCommand(
    // PROJECT_ID, CAMPAIGN_ID, EXECUTION_ID, Channel.PUSH, MESSAGE, null, credential,
    // List.of(target1, target2));

    // ChannelAdapter mockAdapter = stubAdapter();
    // when(channelAdapterRegistry.getAdapter(Channel.PUSH)).thenReturn(mockAdapter);

    // deliveryService.deliver(command);

    // verify(deliveryResultPersister).persist(any(DeliveryCommand.class), any());
    // }

    private static ChannelAdapter stubAdapter() {
        return new ChannelAdapter() {
            @Override
            public Channel supportedChannel() {
                return Channel.PUSH;
            }

            @Override
            public List<TargetOutcome> sendBatch(DeliveryCommand command,
                    List<DeliveryTarget> targets) {
                return targets.stream().map(t -> new TargetOutcome(t, DeliveryResult.success()))
                        .toList();
            }
        };
    }

    /** sendBatch 에 들어온 targets 를 그대로 보관 — variables 채워졌는지 단언 용. */
    private static final class CapturingAdapter implements ChannelAdapter {
        private List<DeliveryTarget> captured = List.of();

        @Override
        public Channel supportedChannel() {
            return Channel.PUSH;
        }

        @Override
        public List<TargetOutcome> sendBatch(DeliveryCommand command,
                List<DeliveryTarget> targets) {
            this.captured = targets;
            return targets.stream().map(t -> new TargetOutcome(t, DeliveryResult.success()))
                    .toList();
        }

        List<DeliveryTarget> captured() {
            return captured;
        }
    }

}
