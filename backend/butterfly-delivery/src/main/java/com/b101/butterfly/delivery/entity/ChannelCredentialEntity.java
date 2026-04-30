package com.b101.butterfly.delivery.entity;

import com.b101.butterfly.delivery.dto.request.DeliveryCommand;
import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "channel_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_channel_credential_project_channel",
                columnNames = {"projectId", "channel"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelCredentialEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long credentialId;

    @Column(nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryCommand.Channel channel;

    @Column(columnDefinition = "TEXT")
    private String credentialData;

    private boolean active;

    public ChannelCredentialEntity(Long projectId, DeliveryCommand.Channel channel,
                                   String credentialData) {
        this.projectId = projectId;
        this.channel = channel;
        this.credentialData = credentialData;
        this.active = true;
    }

    public void updateCredentialData(String credentialData) {
        this.credentialData = credentialData;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
