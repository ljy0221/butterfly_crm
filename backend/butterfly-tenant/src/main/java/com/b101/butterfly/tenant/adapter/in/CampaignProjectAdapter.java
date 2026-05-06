package com.b101.butterfly.tenant.adapter.in;

import com.b101.butterfly.campaign.messagetemplate.port.ProjectExistencePort;
import com.b101.butterfly.campaign.messagetemplate.port.ProjectInformationPort;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.tenant.error.code.ProjectErrorCode;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignProjectAdapter implements ProjectExistencePort, ProjectInformationPort {

    private final ProjectRepository projectRepository;

    @Override
    public boolean existsProject(Long projectId) {
        return projectRepository.existsById(projectId);
    }

    @Override
    public String findProjectName(Long projectId) {
        if (projectRepository.existsById(projectId)) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        return projectRepository.findNameById(projectId);
    }
}
