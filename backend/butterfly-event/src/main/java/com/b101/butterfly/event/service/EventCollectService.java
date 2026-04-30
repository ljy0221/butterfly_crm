package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.EventCollectRequest;
import com.b101.butterfly.event.dto.response.EventCollectResponse;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.exception.CustomerErrorCode;
import com.b101.butterfly.event.messaging.EventPayload;
import com.b101.butterfly.event.messaging.EventProducer;
import com.b101.butterfly.event.repository.EventCategoryRepository;
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

    @Transactional(readOnly = true)
    public EventCollectResponse collect(Long projectId, EventCollectRequest request) {
        EventCategory category = eventCategoryRepository
                .findByProjectIdAndName(projectId, request.eventCategory())
                .orElseThrow(() -> new BusinessException(CustomerErrorCode.CATEGORY_NOT_FOUND));

        String eventUuid = UUID.randomUUID().toString();
        // TODO: event_schemas 도입 시 (categoryId, is_current=true) lookup으로 교체
        int schemaVersion = 1;

        String eventDataJson = serializeEventData(request.eventData());

        EventPayload payload = new EventPayload(
                eventUuid,
                projectId,
                request.userId(),
                request.deviceId(),
                category.getId(),
                request.occurredAt(),
                schemaVersion,
                eventDataJson
        );

        try {
            eventProducer.send(payload);
        } catch (RuntimeException e) {
            log.error("Event publish failed. eventUuid={}, projectId={}", eventUuid, projectId, e);
            throw new BusinessException(CustomerErrorCode.INTERNAL_ERROR, e);
        }

        return new EventCollectResponse(eventUuid, LocalDateTime.now(), schemaVersion);
    }

    private String serializeEventData(JsonNode eventData) {
        return eventData == null || eventData.isNull() ? null : eventData.toString();
    }
}
