package com.b101.butterfly.event.entity;

import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_USERS_PROJECT_EXTID",
                columnNames = {"project_id", "external_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    public static final String PLACEHOLDER_EXTERNAL_ID = "__anonymous__";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "push_opt_in", nullable = false)
    private Boolean pushOptIn;

    @Column(name = "signed_up_at")
    private LocalDateTime signedUpAt;

    @Builder
    public User(Long projectId, String externalId, String email, String phone,
                LocalDate birthDate, Boolean pushOptIn, LocalDateTime signedUpAt) {
        this.projectId = projectId;
        this.externalId = externalId;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.pushOptIn = pushOptIn != null ? pushOptIn : Boolean.TRUE;
        this.signedUpAt = signedUpAt;
    }

    public boolean isPlaceholder() {
        return PLACEHOLDER_EXTERNAL_ID.equals(externalId);
    }
}
