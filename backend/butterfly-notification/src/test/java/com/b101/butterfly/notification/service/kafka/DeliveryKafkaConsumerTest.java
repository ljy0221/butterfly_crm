package com.b101.butterfly.notification.service.kafka;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.Channel;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.DeliveryTarget;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.FcmCredential;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.MessagePayload;
import com.b101.butterfly.notification.service.DeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryKafkaConsumerTest {

    @Mock private DeliveryService deliveryService;

    @InjectMocks private DeliveryKafkaConsumer consumer;

    private static final MessagePayload MESSAGE = new MessagePayload("title", "body", null, null, null);

    @Test
    @DisplayName("정상 메시지를 수신하면 deliveryService.deliver()를 호출한다")
    void consume_callsDeliver() {
        DeliveryCommand command = new DeliveryCommand(
                1L, 100L, 999L, Channel.PUSH, MESSAGE, List.of(1L, 2L), null, null);

        consumer.consume(command);

        verify(deliveryService).deliver(command);
    }

    @Test
    @DisplayName("credential과 targets가 직접 포함된 메시지도 정상 위임한다")
    void consume_withDirectCredentialAndTargets() {
        FcmCredential credential = new FcmCredential("{\"type\":\"service_account\"}");
        DeliveryTarget target = new DeliveryTarget(1L, 10L, "token-abc", null, null);
        DeliveryCommand command = new DeliveryCommand(
                1L, 100L, 999L, Channel.PUSH, MESSAGE, null, credential, List.of(target));

        consumer.consume(command);

        verify(deliveryService).deliver(command);
    }

    @Test
    @DisplayName("deliver()에서 예외가 발생하면 그대로 재throw한다")
    void consume_rethrowsException() {
        DeliveryCommand command = new DeliveryCommand(
                1L, 100L, 999L, Channel.PUSH, MESSAGE, List.of(1L), null, null);

        RuntimeException exception = new RuntimeException("발송 실패");
        doThrow(exception).when(deliveryService).deliver(command);

        assertThatThrownBy(() -> consumer.consume(command))
                .isSameAs(exception);
    }
}
