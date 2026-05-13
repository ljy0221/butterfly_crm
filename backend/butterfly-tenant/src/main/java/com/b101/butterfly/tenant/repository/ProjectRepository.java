package com.b101.butterfly.tenant.repository;

import com.b101.butterfly.tenant.entity.Project;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsById(Long id);

    boolean existsByBusinessNo(String businessNo);

    Optional<Project> findByCompanyAuthNo(String companyAuthNo);

    Optional<Project> findByPublicApiKey(String publicApiKey);

    @Query("SELECT p.name FROM Project p WHERE p.id = :projectId")
    String findNameById(@Param("projectId") Long projectId);
}
