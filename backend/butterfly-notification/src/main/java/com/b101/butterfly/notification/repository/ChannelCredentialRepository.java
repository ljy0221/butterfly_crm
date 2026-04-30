package com.b101.butterfly.notification.repository;

import com.b101.butterfly.notification.dto.request.DeliveryCommand;
import com.b101.butterfly.notification.entity.ChannelCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelCredentialRepository extends JpaRepository<ChannelCredentialEntity, Long> {

    Optional<ChannelCredentialEntity> findByProjectIdAndChannelAndActiveTrue(
            Long projectId, DeliveryCommand.Channel channel);

    Optional<ChannelCredentialEntity> findByProjectIdAndChannel(
            Long projectId, DeliveryCommand.Channel channel);
}
