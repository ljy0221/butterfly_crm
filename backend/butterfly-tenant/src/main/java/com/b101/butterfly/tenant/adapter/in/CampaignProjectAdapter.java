package com.b101.butterfly.tenant.adapter.in;

import com.b101.butterfly.campaign.messagetemplate.port.ProjectExistencePort;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignProjectAdapter implements ProjectExistencePort {

    private final ProjectRepository projectRepository;

    @Override
    public boolean existsProject(Long projectId) {
        return projectRepository.existsById(projectId);
    }
}
