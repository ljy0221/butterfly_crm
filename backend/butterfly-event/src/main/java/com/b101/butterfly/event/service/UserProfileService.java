package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.response.UserProfileResponse;
import com.b101.butterfly.event.dto.response.UserProfileResponse.DeviceSummary;
import com.b101.butterfly.event.dto.response.UserProfileResponse.RecentEvent;
import com.b101.butterfly.event.dto.response.UserProfileResponse.Summary;
import com.b101.butterfly.event.entity.Device;
import com.b101.butterfly.event.entity.Event;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.exception.UserProfileErrorCode;
import com.b101.butterfly.event.repository.DeviceRepository;
import com.b101.butterfly.event.repository.EventRepository;
import com.b101.butterfly.event.repository.UserRepository;
import com.b101.butterfly.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private static final String PURCHASE_CATEGORY = "purchase";

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final EventRepository eventRepository;

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

        Summary summary = new Summary(
                total,
                recent.isEmpty() ? null : recent.get(0).getOccurredAt(),
                purchases
        );

        List<RecentEvent> recentEvents = recent.stream()
                .map(this::toRecentEvent)
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

    private RecentEvent toRecentEvent(Event e) {
        return new RecentEvent(
                e.getId(),
                // categoryId를 문자열로 노출. 향후 EventCategoryRepository를 주입해
                // 카테고리명으로 조회하는 경로로 확장 가능 (시연 범위에서는 categoryId로 충분).
                String.valueOf(e.getCategoryId()),
                e.getOccurredAt(),
                e.getEventData()
        );
    }
}
