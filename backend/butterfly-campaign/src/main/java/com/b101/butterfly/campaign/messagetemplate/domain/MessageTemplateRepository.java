package com.b101.butterfly.campaign.messagetemplate.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    Page<MessageTemplate> findAllByProjectId(Long projectId, Pageable pageable);
}
