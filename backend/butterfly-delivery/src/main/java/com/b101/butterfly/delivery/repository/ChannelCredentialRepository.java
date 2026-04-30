package com.b101.butterfly.delivery.repository;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.delivery.entity.ChannelCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelCredentialRepository extends JpaRepository<ChannelCredentialEntity, Long> {

    Optional<ChannelCredentialEntity> findByProjectIdAndChannelAndActiveTrue(
            Long projectId, DeliveryCommand.Channel channel);

    Optional<ChannelCredentialEntity> findByProjectIdAndChannel(
            Long projectId, DeliveryCommand.Channel channel);
}
