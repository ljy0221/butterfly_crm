package com.b101.butterfly.tenant.repository;

import com.b101.butterfly.tenant.constant.ApplicationStatus;
import com.b101.butterfly.tenant.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    boolean existsByBusinessNo(String businessNo);

    Page<Application> findAllByStatus(ApplicationStatus status, Pageable pageable);
}
