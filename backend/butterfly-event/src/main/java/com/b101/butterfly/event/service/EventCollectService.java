package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.EventCollectRequest;
import com.b101.butterfly.event.dto.response.EventCollectResponse;
import com.b101.butterfly.event.entity.Device;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.messaging.EventPayload;
import com.b101.butterfly.event.messaging.EventProducer;
import com.b101.butterfly.event.repository.DeviceRepository;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.UserRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventCollectService {

    private final EventProducer eventProducer;
    private final EventCategoryRepository eventCategoryRepository;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public EventCollectResponse collect(Long projectId, EventCollectRequest request) {
        EventCategory category = eventCategoryRepository
                .findByProjectIdAndName(projectId, request.eventCategory())
                .orElseThrow(() -> new BusinessException(EventErrorCode.CATEGORY_NOT_FOUND));

        User user = lookupOrCreateUser(projectId, request.userId());
        Device device = lookupDevice(projectId, request.deviceId());

        String eventUuid = UUID.randomUUID().toString();
        // TODO: event_schemas 도입 시 (categoryId, is_current=true) lookup으로 교체
        int schemaVersion = 1;

        String eventDataJson = serializeEventData(request.eventData());

        EventPayload payload = new EventPayload(
                eventUuid,
                projectId,
                user.getId(),
                device.getId(),
                category.getId(),
                request.occurredAt(),
                schemaVersion,
                eventDataJson
        );

        try {
            eventProducer.send(payload);
        } catch (RuntimeException e) {
            log.error("Event publish failed. eventUuid={}, projectId={}", eventUuid, projectId, e);
            throw new BusinessException(EventErrorCode.INTERNAL_ERROR, e);
        }

        return new EventCollectResponse(eventUuid, LocalDateTime.now(), schemaVersion);
    }

    private User lookupOrCreateUser(Long projectId, String externalUserId) {
        String key = (externalUserId != null && !externalUserId.isBlank())
                ? externalUserId
                : User.PLACEHOLDER_EXTERNAL_ID;

        return userRepository.findByProjectIdAndExternalId(projectId, key)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .projectId(projectId)
                                .externalId(key)
                                .build()
                ));
    }

    private Device lookupDevice(Long projectId, String externalDeviceId) {
        return deviceRepository.findByProjectIdAndExternalId(projectId, externalDeviceId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.DEVICE_NOT_REGISTERED));
    }

    private String serializeEventData(JsonNode eventData) {
        return eventData == null || eventData.isNull() ? null : eventData.toString();
    }
}
