package com.b101.butterfly.auth.entity;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(nullable = false, length = 11)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 10)
    private RoleType roleType;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Member(Long projectId, String email, String passwordHash,
                  String name, String phone, LocalDate birthDate, RoleType roleType) {
        this.projectId = projectId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
        this.roleType = roleType;
    }

    public void updatePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateRole(RoleType roleType) {
        this.roleType = roleType;
    }

    public void assignProject(Long projectId) {
        this.projectId = projectId;
    }

    public void updatePhone(String phone) {
        this.phone = phone;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }
}
