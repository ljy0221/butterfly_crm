package com.b101.butterfly.event.service;

import com.b101.butterfly.event.dto.request.DeviceRegisterRequest;
import com.b101.butterfly.event.dto.response.DeviceRegisterResponse;
import com.b101.butterfly.event.entity.Device;
import com.b101.butterfly.event.entity.User;
import com.b101.butterfly.event.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final UserDirectory userDirectory;
    private final DeviceRepository deviceRepository;

    @Transactional
    public DeviceRegisterResponse register(Long projectId, DeviceRegisterRequest request) {
        User user = userDirectory.lookupOrCreate(projectId, request.userId());
        LocalDateTime now = LocalDateTime.now();

        Device device = deviceRepository
                .findByProjectIdAndExternalId(projectId, request.deviceId())
                .map(existing -> {
                    existing.applyRegistration(
                            user.getId(),
                            request.platform(),
                            request.pushToken(),
                            now,
                            request.appVersion(),
                            request.osVersion()
                    );
                    return existing;
                })
                .orElseGet(() -> deviceRepository.save(
                        Device.builder()
                                .projectId(projectId)
                                .userId(user.getId())
                                .externalId(request.deviceId())
                                .platform(request.platform())
                                .pushToken(request.pushToken())
                                .pushTokenUpdatedAt(now)
                                .tokenValid(!request.pushToken().isEmpty())
                                .appVersion(request.appVersion())
                                .osVersion(request.osVersion())
                                .build()
                ));

        return new DeviceRegisterResponse(device.getId(), device.getTokenValid());
    }
}
