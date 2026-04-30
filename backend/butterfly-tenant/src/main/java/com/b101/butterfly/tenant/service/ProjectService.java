package com.b101.butterfly.tenant.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.tenant.dto.response.ProjectResponse;
import com.b101.butterfly.tenant.entity.Project;
import com.b101.butterfly.tenant.exception.TenantErrorCode;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectResponse getProject(Long projectId) {
        return ProjectResponse.from(findProject(projectId));
    }

    @Transactional
    public void deleteProject(Long projectId) {
        Project project = findProject(projectId);
        // TODO(P7): 진행 중인 캠페인 확인 — campaign 모듈 이벤트 연동 후 PROJECT_HAS_ACTIVE_CAMPAIGN 처리
        project.softDelete();
    }

    Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(TenantErrorCode.PROJECT_NOT_FOUND));
    }
}
