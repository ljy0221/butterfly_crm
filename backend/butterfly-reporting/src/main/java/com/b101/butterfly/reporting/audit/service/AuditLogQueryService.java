package com.b101.butterfly.reporting.audit.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.audit.dto.AuditLogDetailResponse;
import com.b101.butterfly.reporting.audit.dto.AuditLogListItem;
import com.b101.butterfly.reporting.audit.dto.AuditLogQuery;
import com.b101.butterfly.reporting.audit.entity.AuditLog;
import com.b101.butterfly.reporting.audit.port.MemberLookupPort;
import com.b101.butterfly.reporting.audit.port.MemberLookupView;
import com.b101.butterfly.reporting.audit.repository.AuditLogRepository;
import com.b101.butterfly.reporting.audit.repository.AuditLogSpecifications;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 조회 서비스 (API #20, #21).
 *
 * <p>append-only 정책에 따라 본 서비스는 SELECT만 수행한다. 발행은 도메인이 직접 이벤트로 publish하고
 * {@code AuditEventListener}가 INSERT한다 (ADR-0009).
 *
 * <p>memberId → memberName/memberEmail 변환은 {@link MemberLookupPort}로 위임한다. 구현 부재 시
 * (테스트 등) lookup 결과는 빈 맵이 되고 응답은 ID-only로 폴백한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectProvider<MemberLookupPort> memberLookupPort;

    public PageResponse<AuditLogListItem> getList(AuditLogQuery query, int page, int size) {
        if (query.startDate() != null && query.endDate() != null
                && query.startDate().isAfter(query.endDate())) {
            throw new BusinessException(AnalyticsErrorCode.INVALID_DATE_RANGE);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<AuditLog> spec = AuditLogSpecifications.withFilters(
                query.projectId(),
                query.memberId(),
                query.action(),
                query.targetType(),
                query.startDate(),
                query.endDate()
        );

        Page<AuditLog> result = auditLogRepository.findAll(spec, pageable);
        List<AuditLog> rows = result.getContent();

        Map<Long, MemberLookupView> nameMap = lookupMembers(rows);

        List<AuditLogListItem> items = rows.stream()
                .map(log -> AuditLogListItem.from(log, nameMap.get(log.getMemberId())))
                .toList();

        return PageResponse.of(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }

    public AuditLogDetailResponse getDetail(Long auditId) {
        AuditLog log = auditLogRepository.findById(auditId)
                .orElseThrow(() -> new BusinessException(AnalyticsErrorCode.AUDIT_NOT_FOUND));
        MemberLookupView member = memberLookupPort.getIfAvailable() != null
                ? memberLookupPort.getObject().find(log.getMemberId()).orElse(null)
                : null;
        return AuditLogDetailResponse.from(log, member);
    }

    private Map<Long, MemberLookupView> lookupMembers(List<AuditLog> rows) {
        MemberLookupPort port = memberLookupPort.getIfAvailable();
        if (port == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = rows.stream()
                .map(AuditLog::getMemberId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return port.findAll(ids);
    }
}
