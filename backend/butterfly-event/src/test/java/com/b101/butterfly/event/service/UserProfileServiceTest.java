package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.response.UserListItemResponse;
import com.b101.butterfly.event.dto.response.UserProfileResponse;
import com.b101.butterfly.event.entity.Device;
import com.b101.butterfly.event.entity.Platform;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.repository.DeviceRepository;
import com.b101.butterfly.event.repository.EventCategoryRepository;
import com.b101.butterfly.event.repository.EventRepository;
import com.b101.butterfly.event.repository.UserActivitySummaryRow;
import com.b101.butterfly.event.repository.UserRepository;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserRepository userRepository;
    @Mock DeviceRepository deviceRepository;
    @Mock EventRepository eventRepository;
    @Mock EventCategoryRepository eventCategoryRepository;

    @InjectMocks UserProfileService service;

    @Test
    void listUsers_returns_db_users_with_activity_summary() {
        Long projectId = 1L;
        User user = User.builder()
                .projectId(projectId)
                .externalId("user-73")
                .email("user-73@example.com")
                .phone("010-1234-5678")
                .pushOptIn(true)
                .signedUpAt(LocalDateTime.of(2026, 4, 22, 10, 30))
                .build();
        ReflectionTestUtils.setField(user, "id", 73L);

        UserActivitySummaryRow row = new UserActivitySummaryRow() {
            @Override public Long getUserId() { return 73L; }
            @Override public Long getTotalEvents() { return 47L; }
            @Override public LocalDateTime getLastSeenAt() {
                return LocalDateTime.of(2026, 5, 14, 15, 30);
            }
        };

        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.searchCustomers(projectId, "user", pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));
        when(eventRepository.summarizeByProjectIdAndUserIds(projectId, List.of(73L)))
                .thenReturn(List.of(row));

        PageResponse<UserListItemResponse> response = service.listUsers(projectId, " user ", pageable);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        UserListItemResponse item = response.content().get(0);
        assertThat(item.userId()).isEqualTo(73L);
        assertThat(item.externalId()).isEqualTo("user-73");
        assertThat(item.email()).isEqualTo("user-73@example.com");
        assertThat(item.phone()).isEqualTo("010-1234-5678");
        assertThat(item.totalEvents()).isEqualTo(47L);
    }

    @Test
    void getProfile_returns_user_device_summary_events() {
        Long projectId = 1L;
        User user = User.builder()
                .projectId(projectId)
                .externalId("user-73")
                .pushOptIn(true)
                .signedUpAt(LocalDateTime.of(2026, 4, 22, 10, 30))
                .build();
        ReflectionTestUtils.setField(user, "id", 73L);

        Device device = Device.builder()
                .projectId(projectId).userId(73L).externalId("dev-73")
                .platform(Platform.IOS)
                .pushToken("tok")
                .pushTokenUpdatedAt(LocalDateTime.now())
                .tokenValid(true)
                .appVersion("2.1.0").osVersion("17.0")
                .lastActiveAt(LocalDateTime.now())
                .build();

        when(userRepository.findById(73L)).thenReturn(Optional.of(user));
        when(deviceRepository.findFirstByProjectIdAndUserIdOrderByLastActiveAtDesc(projectId, 73L))
                .thenReturn(Optional.of(device));
        when(eventRepository.countByProjectIdAndUserId(projectId, 73L)).thenReturn(47L);
        when(eventRepository.findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc(projectId, 73L))
                .thenReturn(List.of());

        UserProfileResponse response = service.getProfile(projectId, 73L);

        assertThat(response.userId()).isEqualTo(73L);
        assertThat(response.externalId()).isEqualTo("user-73");
        assertThat(response.pushOptIn()).isTrue();
        assertThat(response.device().platform()).isEqualTo("IOS");
        assertThat(response.device().appVersion()).isEqualTo("2.1.0");
        assertThat(response.device().osVersion()).isEqualTo("17.0");
        assertThat(response.device().tokenValid()).isTrue();
        assertThat(response.summary().totalEvents()).isEqualTo(47L);
        assertThat(response.recentEvents()).isEmpty();
    }

    @Test
    void getProfile_throws_when_user_not_found() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(1L, 999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getProfile_throws_when_user_belongs_to_other_project() {
        User user = User.builder().projectId(2L).externalId("u").pushOptIn(true).build();
        ReflectionTestUtils.setField(user, "id", 73L);
        when(userRepository.findById(73L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getProfile(1L, 73L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void getProfile_returns_null_device_when_no_device_registered() {
        Long projectId = 1L;
        User user = User.builder()
                .projectId(projectId)
                .externalId("user-200")
                .pushOptIn(true)
                .signedUpAt(LocalDateTime.of(2026, 3, 1, 9, 0))
                .build();
        ReflectionTestUtils.setField(user, "id", 200L);

        when(userRepository.findById(200L)).thenReturn(Optional.of(user));
        when(deviceRepository.findFirstByProjectIdAndUserIdOrderByLastActiveAtDesc(projectId, 200L))
                .thenReturn(Optional.empty());
        when(eventRepository.countByProjectIdAndUserId(projectId, 200L)).thenReturn(0L);
        when(eventRepository.findTop20ByProjectIdAndUserIdOrderByOccurredAtDesc(projectId, 200L))
                .thenReturn(List.of());

        UserProfileResponse response = service.getProfile(projectId, 200L);

        assertThat(response.device()).isNull();
        assertThat(response.summary().totalEvents()).isZero();
        assertThat(response.summary().lastSeenAt()).isNull();
        assertThat(response.recentEvents()).isEmpty();
    }
}
