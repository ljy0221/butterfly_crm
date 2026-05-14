package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.response.UserProfileResponse;
import com.b101.butterfly.event.dto.response.UserProfileResponse.DeviceSummary;
import com.b101.butterfly.event.dto.response.UserProfileResponse.RecentEvent;
import com.b101.butterfly.event.dto.response.UserProfileResponse.Summary;
import com.b101.butterfly.event.entity.Device;
import com.b101.butterfly.event.entity.Event;
import com.b101.butterfly.event.entity.EventCategory;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.exception.UserProfileErrorCode;
import com.b101.butterfly.event.repository.DeviceRepository;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventRepository;
import com.b101.butterfly.event.repository.UserRepository;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private static final String PURCHASE_CATEGORY = "purchase";

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final EventRepository eventRepository;
    private final EventCategoryRepository eventCategoryRepository;

    public UserProfileResponse getProfile(Long projectId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserProfileErrorCode.USER_NOT_FOUND));
        if (!user.getProjectId().equals(projectId)) {
            throw new BusinessException(UserProfileErrorCode.USER_NOT_IN_PROJECT);
        }

        DeviceSummary deviceSummary = deviceRepository
                .findFirstByProjectIdAndUserIdOrderByLastActiveAtDesc(projectId, userId)
                .map(this::toDeviceSummary)
                .orElse(null);

        long total = eventRepository.countByProjectIdAndUserId(projectId, userId);
        long purchases = eventRepository
                .countByProjectIdAndUserIdAndCategoryName(projectId, userId, PURCHASE_CATEGORY);

        List<Event> recent = eventRepository
                .findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc(projectId, userId);

        Map<Long, String> categoryNameById = resolveCategoryNames(projectId, recent);

        Summary summary = new Summary(
                total,
                recent.isEmpty() ? null : recent.get(0).getOccurredAt(),
                purchases
        );

        List<RecentEvent> recentEvents = recent.stream()
                .map(e -> toRecentEvent(e, categoryNameById))
                .toList();

        return new UserProfileResponse(
                user.getId(),
                user.getExternalId(),
                user.getPushOptIn(),
                user.getSignedUpAt(),
                deviceSummary,
                summary,
                recentEvents
        );
    }

    private DeviceSummary toDeviceSummary(Device d) {
        return new DeviceSummary(
                d.getPlatform().name(),
                d.getAppVersion(),
                d.getOsVersion(),
                d.getTokenValid(),
                d.getLastActiveAt()
        );
    }

    private Map<Long, String> resolveCategoryNames(Long projectId, List<Event> events) {
        if (events.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = events.stream()
                .map(Event::getCategoryId)
                .collect(Collectors.toSet());
        return eventCategoryRepository.findAllByProjectIdAndIdIn(projectId, ids).stream()
                .collect(Collectors.toMap(EventCategory::getId, EventCategory::getName));
    }

    private RecentEvent toRecentEvent(Event e, Map<Long, String> categoryNameById) {
        String name = categoryNameById.getOrDefault(
                e.getCategoryId(), String.valueOf(e.getCategoryId()));
        return new RecentEvent(
                e.getId(),
                name,
                e.getOccurredAt(),
                e.getEventData()
        );
    }
}
