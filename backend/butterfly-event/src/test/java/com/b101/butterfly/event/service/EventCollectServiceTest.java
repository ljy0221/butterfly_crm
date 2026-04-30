package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.EventCollectRequest;
import com.b101.butterfly.event.dto.response.EventCollectResponse;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.messaging.EventPayload;
import com.b101.butterfly.event.messaging.EventProducer;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCollectServiceTest {

    @Mock private EventProducer eventProducer;
    @Mock private EventCategoryRepository eventCategoryRepository;

    @InjectMocks private EventCollectService eventCollectService;

    private static final Long PROJECT_ID = 100L;
    private static final Long USER_ID = 7L;
    private static final Long DEVICE_ID = 42L;
    private static final Long CATEGORY_ID = 1L;
    private static final String CATEGORY_NAME = "page_view";
    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 4, 30, 12, 0);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventCategory category() {
        EventCategory c = EventCategory.builder()
                .projectId(PROJECT_ID)
                .name(CATEGORY_NAME)
                .type(EventCategoryType.CUSTOM)
                .description(null)
                .build();
        ReflectionTestUtils.setField(c, "id", CATEGORY_ID);
        return c;
    }

    private EventCollectRequest request() throws Exception {
        return new EventCollectRequest(
                USER_ID,
                DEVICE_ID,
                CATEGORY_NAME,
                OCCURRED_AT,
                MAPPER.readTree("{\"page\":\"/home\"}")
        );
    }

    @Test
    @DisplayName("collect — 정상 흐름: Kafka publish 후 response 반환, payload 필드가 request와 일치")
    void collectPublishesAndReturnsResponse() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));

        EventCollectResponse response = eventCollectService.collect(PROJECT_ID, request());

        ArgumentCaptor<EventPayload> captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(eventProducer).send(captor.capture());
        EventPayload payload = captor.getValue();

        assertThat(payload.projectId()).isEqualTo(PROJECT_ID);
        assertThat(payload.userId()).isEqualTo(USER_ID);
        assertThat(payload.deviceId()).isEqualTo(DEVICE_ID);
        assertThat(payload.categoryId()).isEqualTo(CATEGORY_ID);
        assertThat(payload.occurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(payload.schemaVersion()).isEqualTo(1);
        assertThat(payload.eventUuid()).isNotBlank();
        assertThat(payload.eventData()).contains("\"page\":\"/home\"");

        assertThat(response.eventId()).isEqualTo(payload.eventUuid());
        assertThat(response.schemaVersion()).isEqualTo(1);
        assertThat(response.acceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("collect — eventData가 null이면 payload.eventData도 null")
    void collectAllowsNullEventData() {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));

        EventCollectRequest req = new EventCollectRequest(
                USER_ID, DEVICE_ID, CATEGORY_NAME, OCCURRED_AT, null
        );

        eventCollectService.collect(PROJECT_ID, req);

        ArgumentCaptor<EventPayload> captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(eventProducer).send(captor.capture());
        assertThat(captor.getValue().eventData()).isNull();
    }

    @Test
    @DisplayName("collect — 카테고리 미존재 시 CATEGORY_NOT_FOUND 던지고 publish 안 함")
    void collectThrowsWhenCategoryMissing() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.empty());

        EventCollectRequest req = request();

        assertThatThrownBy(() -> eventCollectService.collect(PROJECT_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CATEGORY_NOT_FOUND);

        verify(eventProducer, never()).send(any());
    }

    @Test
    @DisplayName("collect — Producer가 RuntimeException 던지면 INTERNAL_ERROR로 래핑")
    void collectWrapsProducerFailureAsInternalError() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        doThrow(new IllegalStateException("kafka down")).when(eventProducer).send(any());

        EventCollectRequest req = request();

        assertThatThrownBy(() -> eventCollectService.collect(PROJECT_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.INTERNAL_ERROR);
    }
}
