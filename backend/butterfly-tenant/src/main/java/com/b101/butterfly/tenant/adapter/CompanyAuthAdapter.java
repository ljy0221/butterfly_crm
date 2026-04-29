package com.b101.butterfly.tenant.adapter;

import com.b101.butterfly.auth.port.CompanyAuthPort;
import com.b101.butterfly.tenant.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CompanyAuthAdapter implements CompanyAuthPort {

    private final ProjectRepository projectRepository;

    @Override
    public Optional<Long> findProjectIdByCompanyAuthNo(String companyAuthNo) {
        return projectRepository.findByCompanyAuthNo(companyAuthNo)
                .map(project -> project.getId());
    }
}
