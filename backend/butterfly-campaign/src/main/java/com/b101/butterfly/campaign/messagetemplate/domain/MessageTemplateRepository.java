package com.b101.butterfly.campaign.messagetemplate.domain;

import com.b101.butterfly.campaign.messagetemplate.exception.code.MessageTemplateErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

    Page<MessageTemplate> findAllByProjectId(Long projectId, Pageable pageable);

    Optional<MessageTemplate> findByMessageTemplateId(Long messageTemplateId);

    default MessageTemplate getByMessageTemplateId(Long templateId) {
        return findByMessageTemplateId(templateId)
                .orElseThrow(
                        () -> new BusinessException(MessageTemplateErrorCode.TEMPLATE_NOT_FOUND));
    }

}
