package com.b101.butterfly.reporting.reconciliation.service;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.global.response.PageResponse;
import com.b101.butterfly.reporting.exception.AnalyticsErrorCode;
import com.b101.butterfly.reporting.notification.repository.NotificationCountSummary;
import com.b101.butterfly.reporting.notification.repository.ReportingNotificationLogRepository;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationItem;
import com.b101.butterfly.reporting.reconciliation.dto.ReconciliationQuery;
import com.b101.butterfly.reporting.reconciliation.entity.SendReconciliation;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionInfo;
import com.b101.butterfly.reporting.reconciliation.port.CampaignExecutionPort;
import com.b101.butterfly.reporting.reconciliation.repository.SendReconciliationRepository;
import com.b101.butterfly.reporting.reconciliation.repository.SendReconciliationSpecifications;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * 발송 정합성 검증 도메인 서비스 (S14P31B101-126 / 126-A).
 *
 * <p>외부 회차 메타({@link CampaignExecutionPort})와 내부 발송 원장({@link
 * ReportingNotificationLogRepository})을 비교해 불일치를 {@link SendReconciliation} 행으로
 * 적재한다. 자동 배치·트리거 API·외부 어댑터는 본 티켓 범위 외 — `S14P31B101-187` (126-B)
 * 에서 본 서비스를 호출하는 형태로 추가된다.
 *
 * <p><b>멱등 정책</b>: 같은 회차×채널을 재검증해도 새 row를 INSERT 한다 — 시간순 이력 보존이
 * 운영 진단(일치율 변화 추적)에 유리하기 때문. 가장 최근 결과만 보고 싶은 호출자는
 * `MAX(checked_at)` 기준 조회를 쓰면 된다.
 */
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final CampaignExecutionPort campaignExecutionPort;
    private final ReportingNotificationLogRepository notificationLogRepository;
    private final SendReconciliationRepository reconciliationRepository;

    /**
     * 단일 회차×채널 검증. 외부 메타가 없으면 {@link NoSuchElementException}.
     *
     * <p>회차의 채널은 외부 메타에서 가져온다 — 한 회차에 여러 채널이 있다면 호출자가
     * 채널별로 본 메서드를 호출하거나, 126-B의 자동 배치/트리거가 채널별로 분해해 호출한다.
     */
    @Transactional
    public SendReconciliation reconcile(Long executionId) {
        CampaignExecutionInfo info = campaignExecutionPort.find(executionId)
                .orElseThrow(() -> new NoSuchElementException(
                        "campaign execution not found: " + executionId));
        return reconcileInfo(info);
    }

    /**
     * {@link CampaignExecutionInfo} 가 이미 있는 경우(자동 배치 등)에 호출 — 외부 조회 1회 절약.
     */
    @Transactional
    public SendReconciliation reconcileInfo(CampaignExecutionInfo info) {
        NotificationCountSummary counts = notificationLogRepository.countsForReconciliation(
                info.executionId(), info.channel());

        int expected = info.targetCount();
        int actual = counts.actualAsInt();
        int sent = counts.sentAsInt();
        int failed = counts.failedAsInt();
        int diff = expected - actual;

        SendReconciliation row = SendReconciliation.create()
                .projectId(info.projectId())
                .campaignId(info.campaignId())
                .campaignExecutionId(info.executionId())
                .channel(info.channel())
                .expectedCount(expected)
                .actualCount(actual)
                .sentCount(sent)
                .failedCount(failed)
                .note(buildNote(diff))
                .checkedAt(LocalDateTime.now())
                .build();

        SendReconciliation saved = reconciliationRepository.save(row);
        if (!saved.isConsistent()) {
            log.warn("send_reconciliations inconsistent execution_id={} channel={} diff={} note={}",
                    info.executionId(), info.channel(), diff, saved.getNote());
        }
        return saved;
    }

    /**
     * API #24 — 정합성 검증 결과 목록 (필터 + 페이지네이션, 최신순).
     */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationItem> getList(ReconciliationQuery query, int page, int size) {
        if (query.startDate() != null && query.endDate() != null
                && query.startDate().isAfter(query.endDate())) {
            throw new BusinessException(AnalyticsErrorCode.INVALID_DATE_RANGE);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkedAt"));
        Specification<SendReconciliation> spec = SendReconciliationSpecifications.withFilters(
                query.projectId(),
                query.campaignId(),
                query.executionId(),
                query.consistent(),
                query.startDate(),
                query.endDate()
        );

        Page<SendReconciliation> result = reconciliationRepository.findAll(spec, pageable);
        return PageResponse.of(
                result.getContent().stream().map(ReconciliationItem::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }

    /**
     * API #25 — 불일치 건만 조회. {@link #getList} 의 specialization으로 항상
     * {@code consistent=false} 필터를 강제한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReconciliationItem> getInconsistent(
            Long projectId, Long campaignId, Long executionId,
            java.time.LocalDate startDate, java.time.LocalDate endDate, int page, int size) {
        return getList(
                new ReconciliationQuery(projectId, campaignId, executionId, false, startDate, endDate),
                page, size
        );
    }

    private static String buildNote(int diff) {
        if (diff == 0) {
            return null;
        }
        if (diff > 0) {
            return "%d건 누락 감지".formatted(diff);
        }
        // diff < 0 — 외부 expected 보다 actual 이 많은 비정상. 로그 적재 측 중복 가능성.
        return "%d건 초과 감지".formatted(-diff);
    }
}
