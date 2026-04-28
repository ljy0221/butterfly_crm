package com.b101.butterfly.tenant.dto.response;

import com.b101.butterfly.tenant.constant.ApplicationStatus;
import com.b101.butterfly.tenant.entity.Application;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ApplicationResponse(
        Long applicationId,
        String applicantName,
        String email,
        String phone,
        LocalDate birthDate,
        String businessName,
        String businessNo,
        ApplicationStatus status,
        String rejectReason,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {
    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getApplicantName(),
                application.getEmail(),
                application.getPhone(),
                application.getBirthDate(),
                application.getBusinessName(),
                application.getBusinessNo(),
                application.getStatus(),
                application.getRejectReason(),
                application.getReviewedAt(),
                application.getCreatedAt()
        );
    }
}
