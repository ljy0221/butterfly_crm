package com.b101.butterfly.tenant.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.event.AuditRecordedEvent;
import com.b101.butterfly.global.util.HashUtils;
import com.b101.butterfly.tenant.constant.AuditAction;
import com.b101.butterfly.tenant.entity.Project;
import com.b101.butterfly.tenant.exception.TenantErrorCode;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class ApiKeyService {

    private final ProjectRepository projectRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public String issueApiKey(Long projectId, Long actorId) {
        Project project = findProject(projectId);

        if (project.getPublicApiKey() != null) {
            throw new BusinessException(TenantErrorCode.API_KEY_ALREADY_EXISTS);
        }

        String apiKey = generateApiKey();
        project.issueApiKey(HashUtils.sha256(apiKey));
        eventPublisher.publishEvent(AuditRecordedEvent.of(
                projectId, actorId, AuditAction.API_KEY_ISSUED, "Project", projectId,
                null, null, Map.of("keyPrefix", apiKey.substring(0, 12) + "...")
        ));
        return apiKey;
    }

    @Transactional
    public String revokeAndReissueApiKey(Long projectId, Long actorId) {
        Project project = findProject(projectId);
        // TODO(P7): 진행 중인 캠페인 확인 후 grace period 처리 — 역할 5 워커 연동
        String apiKey = generateApiKey();
        project.issueApiKey(HashUtils.sha256(apiKey));
        eventPublisher.publishEvent(AuditRecordedEvent.of(
                projectId, actorId, AuditAction.API_KEY_REVOKED, "Project", projectId,
                null, null, Map.of("keyPrefix", apiKey.substring(0, 12) + "...")
        ));
        return apiKey;
    }

    private String generateApiKey() {
        return "pk_live_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(TenantErrorCode.PROJECT_NOT_FOUND));
    }
}
