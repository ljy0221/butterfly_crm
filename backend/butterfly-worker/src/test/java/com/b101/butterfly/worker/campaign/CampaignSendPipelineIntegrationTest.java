package com.b101.butterfly.worker.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;

import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateLookupPort;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.port.UserContactInfo;
import com.b101.butterfly.notification.port.UserContactPort;
import com.b101.butterfly.segment.port.SegmentMemberLookupPort;
import com.b101.butterfly.worker.config.WorkerKafkaConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 캠페인 발송 파이프라인 통합 테스트.
 *
 * <p>흐름:
 * <ol>
 *   <li>테스트가 {@code campaign.send.requested} 토픽에 CampaignSendInstruction 발행</li>
 *   <li>CampaignSendInstructionConsumer가 실제 Kafka consumer로 수신</li>
 *   <li>CampaignSendInstructionProcessor가 mock 포트로 데이터 조회 후 DeliveryCommand 조립</li>
 *   <li>테스트가 {@code delivery-requests} 토픽에서 DeliveryCommand 수신 및 검증</li>
 * </ol>
 */
@SpringJUnitConfig(classes = {
        KafkaAutoConfiguration.class,
        WorkerKafkaConfig.class,
        CampaignSendInstructionConsumer.class,
        CampaignSendInstructionProcessor.class
})
@ActiveProfiles("worker")
@EmbeddedKafka(
        partitions = 1,
        topics = {"campaign.send.requested", "delivery-requests"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.b101.butterfly.*",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
})
@Tag("integration")
@DisplayName("캠페인 발송 파이프라인 통합 테스트")
class CampaignSendPipelineIntegrationTest {

    @MockitoBean
    private MessageTemplateLookupPort messageTemplateLookupPort;

    @MockitoBean
    private SegmentMemberLookupPort segmentMemberLookupPort;

    @MockitoBean
    private UserContactPort userContactPort;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("Happy path — CampaignSendInstruction 발행 시 delivery-requests에 DeliveryCommand가 발행된다")
    void happyPath() throws Exception {
        // given
        Long templateId = 1L;
        Long segmentId = 10L;
        Long projectId = 100L;

        MessageTemplateSnapshot template = new MessageTemplateSnapshot(
                templateId, projectId, Channel.PUSH,
                "테스트 제목", "테스트 본문", "https://image.url"
        );
        given(messageTemplateLookupPort.lookup(templateId)).willReturn(template);

        List<Long> userIds = List.of(1L, 2L, 3L);
        given(segmentMemberLookupPort.getActiveUserIds(segmentId)).willReturn(userIds);

        List<UserContactInfo> contacts = List.of(
                new UserContactInfo(1L, 101L, "token1", "user1@test.com", "010-1111-1111"),
                new UserContactInfo(2L, 102L, "token2", "user2@test.com", "010-2222-2222"),
                new UserContactInfo(3L, 103L, "token3", "user3@test.com", "010-3333-3333")
        );
        given(userContactPort.getContactsByUserIds(projectId, userIds, DeliveryCommand.Channel.PUSH))
                .willReturn(contacts);

        CampaignSendInstruction instruction = CampaignSendInstruction.of(
                999L, 123L, projectId, templateId, segmentId, Channel.PUSH, Instant.now()
        );

        // 출력 토픽 검증용 consumer
        Consumer<String, DeliveryCommand> outputConsumer = createOutputConsumer("earliest");

        // when — 입력 토픽에 CampaignSendInstruction 발행
        KafkaTemplate<String, CampaignSendInstruction> inputTemplate = createInputProducer();
        inputTemplate.send("campaign.send.requested", instruction).get(5, TimeUnit.SECONDS);

        // then — 출력 토픽에서 DeliveryCommand 수신 및 검증
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, DeliveryCommand> records =
                    KafkaTestUtils.getRecords(outputConsumer, Duration.ofSeconds(1));
            assertThat(records.count()).isGreaterThanOrEqualTo(1);

            DeliveryCommand command = records.iterator().next().value();
            assertThat(command.projectId()).isEqualTo(projectId);
            assertThat(command.campaignId()).isEqualTo(123L);
            assertThat(command.channel()).isEqualTo(DeliveryCommand.Channel.PUSH);
            assertThat(command.message().title()).isEqualTo("테스트 제목");
            assertThat(command.message().body()).isEqualTo("테스트 본문");
            assertThat(command.message().imageUrl()).isEqualTo("https://image.url");
            assertThat(command.userIds()).containsExactly(1L, 2L, 3L);
            assertThat(command.targets()).hasSize(3);
            assertThat(command.targets().get(0).pushToken()).isEqualTo("token1");
        });

        outputConsumer.close();
    }

    @Test
    @DisplayName("세그먼트 멤버가 없으면 delivery-requests에 메시지가 발행되지 않는다")
    void noDeliveryWhenSegmentEmpty() throws Exception {
        // given
        Long templateId = 2L;
        Long segmentId = 20L;
        Long projectId = 200L;
        String partitionKey = projectId.toString();

        MessageTemplateSnapshot template = new MessageTemplateSnapshot(
                templateId, projectId, Channel.PUSH,
                "제목", "본문", null
        );
        given(messageTemplateLookupPort.lookup(templateId)).willReturn(template);
        given(segmentMemberLookupPort.getActiveUserIds(segmentId)).willReturn(Collections.emptyList());

        CampaignSendInstruction instruction = CampaignSendInstruction.of(
                888L, 456L, projectId, templateId, segmentId, Channel.PUSH, Instant.now()
        );

        // 출력 토픽 검증용 consumer (latest offset — 이전 테스트 메시지 무시)
        Consumer<String, DeliveryCommand> outputConsumer = createOutputConsumer("latest");

        // when
        KafkaTemplate<String, CampaignSendInstruction> inputTemplate = createInputProducer();
        inputTemplate.send("campaign.send.requested", instruction).get(5, TimeUnit.SECONDS);

        // then — consumer 처리 대기 후 출력 토픽에 해당 projectId의 메시지가 없는지 확인
        Thread.sleep(5_000);
        ConsumerRecords<String, DeliveryCommand> records =
                KafkaTestUtils.getRecords(outputConsumer, Duration.ofSeconds(2));

        long matchCount = StreamSupport.stream(records.spliterator(), false)
                .filter(r -> partitionKey.equals(r.key()))
                .count();
        assertThat(matchCount).isZero();

        outputConsumer.close();
    }

    private KafkaTemplate<String, CampaignSendInstruction> createInputProducer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private Consumer<String, DeliveryCommand> createOutputConsumer(String autoOffsetReset) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-output-group-" + System.nanoTime(),
                "true",
                embeddedKafkaBroker
        );
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.b101.butterfly.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, DeliveryCommand.class.getName());

        DefaultKafkaConsumerFactory<String, DeliveryCommand> factory =
                new DefaultKafkaConsumerFactory<>(props);
        Consumer<String, DeliveryCommand> consumer = factory.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "delivery-requests");
        return consumer;
    }
}
