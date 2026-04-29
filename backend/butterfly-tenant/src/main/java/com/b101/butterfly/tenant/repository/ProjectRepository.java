package com.b101.butterfly.tenant.repository;

import com.b101.butterfly.tenant.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByBusinessNo(String businessNo);

    Optional<Project> findByCompanyAuthNo(String companyAuthNo);

    Optional<Project> findByPublicApiKey(String publicApiKey);
}
