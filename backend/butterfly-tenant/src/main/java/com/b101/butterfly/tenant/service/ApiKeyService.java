package com.b101.butterfly.tenant.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.tenant.entity.Project;
import com.b101.butterfly.tenant.exception.TenantErrorCode;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class ApiKeyService {

    private final ProjectRepository projectRepository;

    @Transactional
    public String issueApiKey(Long projectId) {
        Project project = findProject(projectId);

        if (project.getPublicApiKey() != null) {
            throw new BusinessException(TenantErrorCode.API_KEY_ALREADY_EXISTS);
        }

        String apiKey = generateApiKey();
        project.issueApiKey(apiKey);
        // TODO(P7): AuditRecordedEvent 발행 (API_KEY_ISSUED) — ADR-0009
        return apiKey;
    }

    @Transactional
    public String revokeAndReissueApiKey(Long projectId) {
        Project project = findProject(projectId);
        // TODO(P7): 진행 중인 캠페인 확인 후 grace period 처리 — 역할 5 워커 연동
        String apiKey = generateApiKey();
        project.issueApiKey(apiKey);
        // TODO(P7): AuditRecordedEvent 발행 (API_KEY_REVOKED) — ADR-0009
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
