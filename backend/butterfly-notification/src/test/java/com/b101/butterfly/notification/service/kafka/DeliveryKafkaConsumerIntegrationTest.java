package com.b101.butterfly.notification.service.kafka;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.Channel;
import com.b101.butterfly.notification.dto.request.DeliveryCommand.MessagePayload;
import com.b101.butterfly.notification.service.DeliveryService;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(classes = {KafkaAutoConfiguration.class, DeliveryKafkaConsumer.class})
@EmbeddedKafka(
        partitions = 1,
        topics = "delivery-requests",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.consumer.group-id=delivery-consumer-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.b101.butterfly.notification.dto.request",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.b101.butterfly.notification.dto.request.DeliveryCommand"
})
class DeliveryKafkaConsumerIntegrationTest {

    @MockitoBean
    private DeliveryService deliveryService;

//    @Autowired
//    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("Kafka 토픽에 메시지를 발행하면 Consumer가 수신하여 deliver()를 호출한다")
    void publishAndConsume(EmbeddedKafkaBroker broker) {
        KafkaTemplate<String, DeliveryCommand> kafkaTemplate = createProducer(broker);

        DeliveryCommand command = new DeliveryCommand(
                1L, 100L, 999L, Channel.PUSH,
                new MessagePayload("title", "body", null, null),
                List.of(1L, 2L),
                null, null);

        kafkaTemplate.send("delivery-requests", command);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(deliveryService).deliver(any(DeliveryCommand.class)));
    }

    private KafkaTemplate<String, DeliveryCommand> createProducer(EmbeddedKafkaBroker broker) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
