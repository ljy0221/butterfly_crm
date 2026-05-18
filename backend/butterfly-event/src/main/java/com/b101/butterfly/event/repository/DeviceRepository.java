package com.b101.butterfly.event.repository;

import com.b101.butterfly.event.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByProjectIdAndExternalId(Long projectId, String externalId);

    Optional<Device> findFirstByProjectIdAndUserIdOrderByLastActiveAtDesc(Long projectId, Long userId);

    Optional<Device> findByExternalId(String externalId);
}
