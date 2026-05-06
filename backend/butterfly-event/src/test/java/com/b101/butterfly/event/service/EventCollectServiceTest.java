package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.EventCollectRequest;
import com.b101.butterfly.event.dto.response.EventCollectResponse;
import com.b101.butterfly.event.entity.Device;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.EventCategoryType;
import com.b101.butterfly.event.entity.Platform;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.messaging.EventPayload;
import com.b101.butterfly.event.messaging.EventProducer;
import com.b101.butterfly.event.entity.EventSchema;
import com.b101.butterfly.event.repository.DeviceRepository;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventSchemaRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventCollectServiceTest {

    @Mock private EventProducer eventProducer;
    @Mock private EventCategoryRepository eventCategoryRepository;
    @Mock private EventSchemaRepository eventSchemaRepository;
    @Mock private UserDirectory userDirectory;
    @Mock private DeviceRepository deviceRepository;

    @InjectMocks private EventCollectService eventCollectService;

    private static final Long PROJECT_ID = 100L;
    private static final Long INTERNAL_USER_ID = 7L;
    private static final Long INTERNAL_DEVICE_ID = 42L;
    private static final Long PLACEHOLDER_USER_ID = 9L;
    private static final Long CATEGORY_ID = 1L;
    private static final String EXTERNAL_USER_ID = "u_kim_xyz";
    private static final String EXTERNAL_DEVICE_ID = "dev_abc789";
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

    private User user(Long id, String externalId) {
        User u = User.builder()
                .projectId(PROJECT_ID)
                .externalId(externalId)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Device device() {
        Device d = Device.builder()
                .projectId(PROJECT_ID)
                .userId(INTERNAL_USER_ID)
                .externalId(EXTERNAL_DEVICE_ID)
                .platform(Platform.ANDROID)
                .pushToken("")
                .pushTokenUpdatedAt(LocalDateTime.now())
                .tokenValid(true)
                .appVersion("2.1.0")
                .osVersion("14")
                .build();
        ReflectionTestUtils.setField(d, "id", INTERNAL_DEVICE_ID);
        return d;
    }

    private EventCollectRequest request(String externalUserId) throws Exception {
        return new EventCollectRequest(
                externalUserId,
                EXTERNAL_DEVICE_ID,
                CATEGORY_NAME,
                OCCURRED_AT,
                MAPPER.readTree("{\"page\":\"/home\"}")
        );
    }

    @Test
    @DisplayName("collect — 정상 흐름: user/device lookup 후 internal PK로 publish")
    void collectPublishesAndReturnsResponse() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(INTERNAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.of(device()));

        EventCollectResponse response = eventCollectService.collect(PROJECT_ID, request(EXTERNAL_USER_ID));

        ArgumentCaptor<EventPayload> captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(eventProducer).send(captor.capture());
        EventPayload payload = captor.getValue();

        assertThat(payload.projectId()).isEqualTo(PROJECT_ID);
        assertThat(payload.userId()).isEqualTo(INTERNAL_USER_ID);
        assertThat(payload.deviceId()).isEqualTo(INTERNAL_DEVICE_ID);
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
    @DisplayName("collect — userId가 null이면 UserDirectory에 null로 위임 (placeholder 처리)")
    void collectAnonymousUserDelegatesNullToUserDirectory() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        when(userDirectory.lookupOrCreate(PROJECT_ID, null))
                .thenReturn(user(PLACEHOLDER_USER_ID, User.PLACEHOLDER_EXTERNAL_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.of(device()));

        eventCollectService.collect(PROJECT_ID, request(null));

        verify(userDirectory).lookupOrCreate(PROJECT_ID, null);

        ArgumentCaptor<EventPayload> payloadCaptor = ArgumentCaptor.forClass(EventPayload.class);
        verify(eventProducer).send(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().userId()).isEqualTo(PLACEHOLDER_USER_ID);
    }

    @Test
    @DisplayName("collect — eventData가 null이면 payload.eventData도 null")
    void collectAllowsNullEventData() {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(INTERNAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.of(device()));

        EventCollectRequest req = new EventCollectRequest(
                EXTERNAL_USER_ID, EXTERNAL_DEVICE_ID, CATEGORY_NAME, OCCURRED_AT, null
        );

        eventCollectService.collect(PROJECT_ID, req);

        ArgumentCaptor<EventPayload> captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(eventProducer).send(captor.capture());
        assertThat(captor.getValue().eventData()).isNull();
    }

    @Test
    @DisplayName("collect — 카테고리 미존재 시 CATEGORY_NOT_FOUND 던지고 user/device lookup 안 함")
    void collectThrowsWhenCategoryMissing() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.empty());

        EventCollectRequest req = request(EXTERNAL_USER_ID);

        assertThatThrownBy(() -> eventCollectService.collect(PROJECT_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.CATEGORY_NOT_FOUND);

        verify(userDirectory, never()).lookupOrCreate(any(), any());
        verify(deviceRepository, never()).findByProjectIdAndExternalId(any(), any());
        verify(eventProducer, never()).send(any());
    }

    @Test
    @DisplayName("collect — 디바이스 미등록 시 DEVICE_NOT_REGISTERED 던지고 publish 안 함")
    void collectThrowsWhenDeviceNotRegistered() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(INTERNAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.empty());

        EventCollectRequest req = request(EXTERNAL_USER_ID);

        assertThatThrownBy(() -> eventCollectService.collect(PROJECT_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.DEVICE_NOT_REGISTERED);

        verify(eventProducer, never()).send(any());
    }

    @Test
    @DisplayName("collect — Producer가 RuntimeException 던지면 INTERNAL_ERROR로 래핑")
    void collectWrapsProducerFailureAsInternalError() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(INTERNAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(eq(PROJECT_ID), eq(EXTERNAL_DEVICE_ID)))
                .thenReturn(Optional.of(device()));
        doThrow(new IllegalStateException("kafka down")).when(eventProducer).send(any());

        EventCollectRequest req = request(EXTERNAL_USER_ID);

        assertThatThrownBy(() -> eventCollectService.collect(PROJECT_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(EventErrorCode.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("collect — 현행 스키마가 등록된 카테고리는 해당 version을 payload에 반영")
    void collectUsesCurrentSchemaVersion() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(INTERNAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.of(device()));

        EventSchema schema = EventSchema.builder()
                .projectId(PROJECT_ID).categoryId(CATEGORY_ID)
                .version(3).schemaDef("[]")
                .effectiveFrom(OCCURRED_AT).isCurrent(true).createdBy(0L)
                .build();
        ReflectionTestUtils.setField(schema, "id", 300L);
        when(eventSchemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID))
                .thenReturn(Optional.of(schema));

        eventCollectService.collect(PROJECT_ID, request(EXTERNAL_USER_ID));

        ArgumentCaptor<EventPayload> captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(eventProducer).send(captor.capture());
        assertThat(captor.getValue().schemaVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("collect — 스키마 미등록 카테고리는 schemaVersion=1로 fallback")
    void collectFallsBackToVersion1WhenNoSchema() throws Exception {
        when(eventCategoryRepository.findByProjectIdAndName(PROJECT_ID, CATEGORY_NAME))
                .thenReturn(Optional.of(category()));
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(INTERNAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.of(device()));
        when(eventSchemaRepository.findByCategoryIdAndIsCurrentTrue(CATEGORY_ID))
                .thenReturn(Optional.empty());

        eventCollectService.collect(PROJECT_ID, request(EXTERNAL_USER_ID));

        ArgumentCaptor<EventPayload> captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(eventProducer).send(captor.capture());
        assertThat(captor.getValue().schemaVersion()).isEqualTo(1);
    }
}
