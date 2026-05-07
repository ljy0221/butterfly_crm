package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.response.ProjectEventResponse;
import com.b101.butterfly.event.dto.response.UserEventResponse;
import com.b101.butterfly.event.entity.Event;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.exception.EventErrorCode;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventRepository;
import com.b101.butterfly.event.repository.UserRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventManagementService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final EventCategoryRepository eventCategoryRepository;

    public PageResponse<UserEventResponse> getUserEvents(
            Long projectId, String externalUserId, Pageable pageable) {

        User user = userRepository.findByProjectIdAndExternalId(projectId, externalUserId)
                .orElseThrow(() -> new BusinessException(EventErrorCode.USER_NOT_FOUND));

        Page<Event> events = eventRepository.findByProjectIdAndUserId(
                projectId, user.getId(), pageable);

        Set<Long> categoryIds = events.stream()
                .map(Event::getCategoryId)
                .collect(Collectors.toSet());

        Map<Long, String> categoryNames = eventCategoryRepository.findAllById(categoryIds)
                .stream()
                .collect(Collectors.toMap(EventCategory::getId, EventCategory::getName));

        List<UserEventResponse> content = events.stream()
                .map(e -> new UserEventResponse(
                        e.getEventUuid(),
                        categoryNames.getOrDefault(e.getCategoryId(), "UNKNOWN"),
                        e.getOccurredAt(),
                        e.getEventData()
                ))
                .toList();

        return PageResponse.of(content, events.getNumber(), events.getSize(), events.getTotalElements());
    }

    public PageResponse<ProjectEventResponse> getProjectEvents(Long projectId, Pageable pageable) {

        Page<Event> events = eventRepository.findByProjectId(projectId, pageable);

        Set<Long> categoryIds = events.stream()
                .map(Event::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, String> categoryNames = eventCategoryRepository.findAllById(categoryIds)
                .stream()
                .collect(Collectors.toMap(EventCategory::getId, EventCategory::getName));

        Set<Long> userIds = events.stream()
                .map(Event::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> externalUserIds = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getExternalId));

        List<ProjectEventResponse> content = events.stream()
                .map(e -> new ProjectEventResponse(
                        e.getEventUuid(),
                        externalUserIds.getOrDefault(e.getUserId(), "UNKNOWN"),
                        categoryNames.getOrDefault(e.getCategoryId(), "UNKNOWN"),
                        e.getOccurredAt(),
                        e.getEventData()
                ))
                .toList();

        return PageResponse.of(content, events.getNumber(), events.getSize(), events.getTotalElements());
    }
}
