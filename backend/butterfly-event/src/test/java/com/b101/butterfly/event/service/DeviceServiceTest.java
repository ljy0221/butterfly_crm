package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.DeviceRegisterRequest;
import com.b101.butterfly.event.dto.response.DeviceRegisterResponse;
import com.b101.butterfly.event.entity.Device;
import com.b101.butterfly.event.entity.Platform;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.repository.DeviceRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock private UserDirectory userDirectory;
    @Mock private DeviceRepository deviceRepository;

    @InjectMocks private DeviceService deviceService;

    private static final Long PROJECT_ID = 100L;
    private static final Long REAL_USER_ID = 7L;
    private static final Long PLACEHOLDER_USER_ID = 9L;
    private static final String EXTERNAL_USER_ID = "u_kim_xyz";
    private static final String EXTERNAL_DEVICE_ID = "dev_abc789";

    private User user(Long id, String externalId) {
        User u = User.builder()
                .projectId(PROJECT_ID)
                .externalId(externalId)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private Device existingDevice(Long ownerUserId, String pushToken, boolean tokenValid) {
        Device d = Device.builder()
                .projectId(PROJECT_ID)
                .userId(ownerUserId)
                .externalId(EXTERNAL_DEVICE_ID)
                .platform(Platform.ANDROID)
                .pushToken(pushToken)
                .pushTokenUpdatedAt(LocalDateTime.now().minusDays(1))
                .tokenValid(tokenValid)
                .appVersion("1.0.0")
                .osVersion("13")
                .build();
        ReflectionTestUtils.setField(d, "id", 42L);
        return d;
    }

    private DeviceRegisterRequest request(String externalUserId, String pushToken) {
        return new DeviceRegisterRequest(
                EXTERNAL_DEVICE_ID,
                externalUserId,
                Platform.ANDROID,
                pushToken,
                "2.1.0",
                "14"
        );
    }

    @Test
    @DisplayName("register — 신규 device: User lookup-or-create + Device 생성, push_token 비어있으면 token_valid=false")
    void registerCreatesNewDeviceWithEmptyPushToken() {
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(REAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeviceRegisterResponse response = deviceService.register(PROJECT_ID, request(EXTERNAL_USER_ID, ""));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        Device saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(saved.getUserId()).isEqualTo(REAL_USER_ID);
        assertThat(saved.getExternalId()).isEqualTo(EXTERNAL_DEVICE_ID);
        assertThat(saved.getPlatform()).isEqualTo(Platform.ANDROID);
        assertThat(saved.getPushToken()).isEmpty();
        assertThat(saved.getTokenValid()).isFalse();
        assertThat(saved.getAppVersion()).isEqualTo("2.1.0");
        assertThat(saved.getOsVersion()).isEqualTo("14");

        assertThat(response.tokenValid()).isFalse();
    }

    @Test
    @DisplayName("register — 신규 device: push_token이 비어있지 않으면 token_valid=true")
    void registerCreatesNewDeviceWithValidPushToken() {
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(REAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DeviceRegisterResponse response = deviceService.register(
                PROJECT_ID, request(EXTERNAL_USER_ID, "fcm_token_xyz"));

        assertThat(response.tokenValid()).isTrue();
    }

    @Test
    @DisplayName("register — 익명 등록: userId null이면 UserDirectory가 placeholder 처리")
    void registerAnonymousDelegatesNullToUserDirectory() {
        when(userDirectory.lookupOrCreate(PROJECT_ID, null))
                .thenReturn(user(PLACEHOLDER_USER_ID, User.PLACEHOLDER_EXTERNAL_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.empty());
        when(deviceRepository.save(any(Device.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        deviceService.register(PROJECT_ID, request(null, ""));

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(PLACEHOLDER_USER_ID);
    }

    @Test
    @DisplayName("register — 기존 device 재호출: applyRegistration으로 user_id rebind + push_token 갱신, save 안 함")
    void registerExistingDeviceRebindAndRefresh() {
        Device existing = existingDevice(PLACEHOLDER_USER_ID, "", false);
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(REAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.of(existing));

        DeviceRegisterResponse response = deviceService.register(
                PROJECT_ID, request(EXTERNAL_USER_ID, "fcm_new_token"));

        // dirty checking 위임 — save 호출 없음
        verify(deviceRepository, never()).save(any());

        // 기존 row가 새 값으로 갱신됨
        assertThat(existing.getUserId()).isEqualTo(REAL_USER_ID);
        assertThat(existing.getPushToken()).isEqualTo("fcm_new_token");
        assertThat(existing.getTokenValid()).isTrue();
        assertThat(existing.getAppVersion()).isEqualTo("2.1.0");
        assertThat(existing.getOsVersion()).isEqualTo("14");

        assertThat(response.tokenValid()).isTrue();
    }

    @Test
    @DisplayName("register — 기존 device가 토큰 만료 상태(token_valid=false)에서 빈 토큰으로 재호출하면 token_valid=false 유지")
    void registerExistingDeviceWithEmptyTokenKeepsTokenInvalid() {
        Device existing = existingDevice(REAL_USER_ID, "stale_token", false);
        when(userDirectory.lookupOrCreate(PROJECT_ID, EXTERNAL_USER_ID))
                .thenReturn(user(REAL_USER_ID, EXTERNAL_USER_ID));
        when(deviceRepository.findByProjectIdAndExternalId(PROJECT_ID, EXTERNAL_DEVICE_ID))
                .thenReturn(Optional.of(existing));

        DeviceRegisterResponse response = deviceService.register(
                PROJECT_ID, request(EXTERNAL_USER_ID, ""));

        assertThat(existing.getPushToken()).isEmpty();
        assertThat(existing.getTokenValid()).isFalse();
        assertThat(response.tokenValid()).isFalse();
    }
}
