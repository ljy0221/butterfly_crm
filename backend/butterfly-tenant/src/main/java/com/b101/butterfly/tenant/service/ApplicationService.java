package com.b101.butterfly.tenant.service;

import com.b101.butterfly.auth.constant.RoleType;
import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.repository.MemberRepository;
import com.b101.butterfly.auth.service.EmailService;
import com.b101.butterfly.auth.service.RedisService;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.tenant.constant.ApplicationStatus;
import com.b101.butterfly.tenant.constant.AuditAction;
import com.b101.butterfly.tenant.dto.request.ApplicationCreateRequest;
import com.b101.butterfly.tenant.dto.request.ApplicationRejectRequest;
import com.b101.butterfly.tenant.dto.response.ApplicationResponse;
import com.b101.butterfly.tenant.entity.Application;
import com.b101.butterfly.tenant.entity.AuditLog;
import com.b101.butterfly.tenant.entity.Project;
import com.b101.butterfly.tenant.exception.TenantErrorCode;
import com.b101.butterfly.tenant.repository.ApplicationRepository;
import com.b101.butterfly.tenant.repository.AuditLogRepository;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class ApplicationService {

    private static final Duration OWNER_LINK_TTL = Duration.ofHours(24);

    private final ApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogRepository auditLogRepository;
    private final RedisService redisService;
    private final EmailService emailService;

    @Transactional
    public Long submit(ApplicationCreateRequest request) {
        if (applicationRepository.existsByBusinessNo(request.businessNo())) {
            throw new BusinessException(TenantErrorCode.DUPLICATE_BUSINESS_NO);
        }

        Application application = Application.builder()
                .applicantName(request.applicantName())
                .email(request.email())
                .phone(request.phone())
                .birthDate(request.birthDate())
                .businessName(request.businessName())
                .businessNo(request.businessNo())
                .build();

        return applicationRepository.save(application).getId();
    }

    public Page<ApplicationResponse> getList(ApplicationStatus status, Pageable pageable) {
        if (status != null) {
            return applicationRepository.findAllByStatus(status, pageable)
                    .map(ApplicationResponse::from);
        }
        return applicationRepository.findAll(pageable)
                .map(ApplicationResponse::from);
    }

    public ApplicationResponse getDetail(Long applicationId) {
        return ApplicationResponse.from(findApplication(applicationId));
    }

    @Transactional
    public void approve(Long applicationId, Long adminMemberId) {
        Application application = findApplication(applicationId);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(TenantErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        application.approve(adminMemberId);

        // OWNER Member 생성 (비밀번호 미설정 상태)
        Member owner = Member.builder()
                .email(application.getEmail())
                .name(application.getApplicantName())
                .phone(application.getPhone())
                .birthDate(application.getBirthDate())
                .roleType(RoleType.OWNER)
                .build();
        memberRepository.save(owner);

        // Project 생성
        String companyAuthNo = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Project project = Project.builder()
                .name(application.getBusinessName())
                .businessNo(application.getBusinessNo())
                .companyAuthNo(companyAuthNo)
                .ownerId(owner.getId())
                .build();
        projectRepository.save(project);

        // OWNER에 프로젝트 연결
        owner.assignProject(project.getId());

        auditLogRepository.save(AuditLog.builder()
                .memberId(adminMemberId)
                .action(AuditAction.APPLICATION_APPROVED)
                .targetType("Application")
                .targetId(applicationId)
                .build());

        // Owner 비밀번호 설정 링크 발송 (24시간 유효)
        String linkToken = UUID.randomUUID().toString();
        redisService.set("auth:owner:link:" + linkToken, String.valueOf(owner.getId()), OWNER_LINK_TTL);
        emailService.sendOwnerInvite(owner.getEmail(), linkToken, companyAuthNo);
    }

    @Transactional
    public void reject(Long applicationId, Long adminMemberId, ApplicationRejectRequest request) {
        Application application = findApplication(applicationId);

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(TenantErrorCode.APPLICATION_ALREADY_PROCESSED);
        }

        application.reject(adminMemberId, request.rejectReason());

        auditLogRepository.save(AuditLog.builder()
                .memberId(adminMemberId)
                .action(AuditAction.APPLICATION_REJECTED)
                .targetType("Application")
                .targetId(applicationId)
                .build());
    }

    private Application findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(TenantErrorCode.APPLICATION_NOT_FOUND));
    }
}
