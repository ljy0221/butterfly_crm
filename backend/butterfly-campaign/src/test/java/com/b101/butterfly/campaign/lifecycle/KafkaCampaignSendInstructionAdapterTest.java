package com.b101.butterfly.campaign.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.campaign.domain.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.campaign.infrastructure.kafka.KafkaCampaignSendInstructionAdapter;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@DisplayName("KafkaCampaignSendInstructionAdapter")
class KafkaCampaignSendInstructionAdapterTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, CampaignSendInstruction> kafkaTemplate =
            (KafkaTemplate<String, CampaignSendInstruction>) mock(KafkaTemplate.class);
    private KafkaCampaignSendInstructionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new KafkaCampaignSendInstructionAdapter(kafkaTemplate);
    }

    @Test
    @DisplayName("토픽 campaign.send.requested + projectId 문자열을 partition key로 사용")
    void publishUsesProjectIdAsPartitionKey() {
        CampaignSendInstruction instruction = sample();
        given(kafkaTemplate.send(anyString(), anyString(), any(CampaignSendInstruction.class)))
                .willReturn(CompletableFuture.completedFuture(sendResult()));

        adapter.publish(instruction);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CampaignSendInstruction> payloadCaptor =
                ArgumentCaptor.forClass(CampaignSendInstruction.class);
        org.mockito.Mockito.verify(kafkaTemplate)
                .send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue())
                .isEqualTo(KafkaCampaignSendInstructionAdapter.TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo("1");
        assertThat(payloadCaptor.getValue()).isEqualTo(instruction);
    }

    @Test
    @DisplayName("publish 도중 ExecutionException 발생 시 IllegalStateException으로 래핑")
    void wrapsExecutionException() {
        CompletableFuture<SendResult<String, CampaignSendInstruction>> failed =
                CompletableFuture.failedFuture(new RuntimeException("broker down"));
        given(kafkaTemplate.send(anyString(), anyString(), any(CampaignSendInstruction.class)))
                .willReturn(failed);

        assertThatThrownBy(() -> adapter.publish(sample()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publish failed");
    }

    private CampaignSendInstruction sample() {
        return CampaignSendInstruction.of(
                123L, 456L, 1L, 789L, null, Channel.PUSH, Instant.parse("2026-05-02T12:00:00Z"));
    }

    private SendResult<String, CampaignSendInstruction> sendResult() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(KafkaCampaignSendInstructionAdapter.TOPIC, 0),
                0L, 0, 0L, 0, 0);
        return new SendResult<>(null, metadata);
    }
}