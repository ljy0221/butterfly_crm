package com.b101.butterfly.auth.entity;

import com.b101.butterfly.auth.constant.ApplicationStatus;
import com.b101.butterfly.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long id;

    @Column(name = "applicant_name", nullable = false, length = 100)
    private String applicantName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 11)
    private String phone;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "business_no", nullable = false, length = 50)
    private String businessNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Builder
    public Application(String applicantName, String email, String phone,
                       LocalDate birthDate, String businessName, String businessNo) {
        this.applicantName = applicantName;
        this.email = email;
        this.phone = phone;
        this.birthDate = birthDate;
        this.businessName = businessName;
        this.businessNo = businessNo;
        this.status = ApplicationStatus.PENDING;
    }

    public void approve(Long adminMemberId) {
        this.status = ApplicationStatus.APPROVED;
        this.reviewedBy = adminMemberId;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Long adminMemberId, String reason) {
        this.status = ApplicationStatus.REJECTED;
        this.reviewedBy = adminMemberId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectReason = reason;
    }
}
