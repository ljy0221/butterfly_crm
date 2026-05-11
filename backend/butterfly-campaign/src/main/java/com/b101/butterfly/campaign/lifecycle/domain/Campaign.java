package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignType;
import com.b101.butterfly.campaign.lifecycle.domain.enums.RepeatType;
import com.b101.butterfly.campaign.lifecycle.domain.enums.SendMode;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "campaigns")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE campaigns SET is_deleted = true, deleted_at = NOW() WHERE campaign_id = ?")
@SQLRestriction("is_deleted = false")
public class Campaign extends SoftDeletedDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "campaign_id")
    private Long campaignId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "message_template_id", nullable = false)
    private Long messageTemplateId;

    @Column(name = "segment_id")
    private Long segmentId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "campaign_name", nullable = false)
    private String campaignName;

    @Column(name = "campaign_description", nullable = false)
    private String campaignDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_type", nullable = false)
    private CampaignType campaignType;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_mode")
    private SendMode sendMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_status", nullable = false)
    private CampaignStatus campaignStatus;

    @Column(name = "recurring_cron")
    private String recurringCron;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_type")
    private RepeatType repeatType;

    @Column(name = "repeat_day_of_week")
    private String repeatDayOfWeek;

    @Column(name = "repeat_day_of_month")
    private Integer repeatDayOfMonth;

    @Column(name = "repeat_time")
    private LocalTime repeatTime;

    @Column(name = "recurring_count")
    private Integer recurringCount;

    @Column(name = "recurring_execution_count", nullable = false)
    private Integer recurringExecutionCount;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    private Campaign(
            Long projectId,
            Long messageTemplateId,
            Long segmentId,
            Long memberId,
            String campaignName,
            String campaignDescription,
            CampaignType campaignType,
            SendMode sendMode,
            Channel channel,
            CampaignStatus campaignStatus,
            String recurringCron,
            RepeatType repeatType,
            String repeatDayOfWeek,
            Integer repeatDayOfMonth,
            LocalTime repeatTime,
            Integer recurringCount,
            LocalDateTime nextRunAt
    ) {
        this.projectId = projectId;
        this.messageTemplateId = messageTemplateId;
        this.segmentId = segmentId;
        this.memberId = memberId;
        this.campaignName = campaignName;
        this.campaignDescription = campaignDescription;
        this.campaignType = campaignType;
        this.sendMode = sendMode;
        this.channel = channel;
        this.campaignStatus = campaignStatus;
        this.recurringCron = recurringCron;
        this.repeatType = repeatType;
        this.repeatDayOfWeek = repeatDayOfWeek;
        this.repeatDayOfMonth = repeatDayOfMonth;
        this.repeatTime = repeatTime;
        this.recurringCount = recurringCount;
        this.recurringExecutionCount = 0;
        this.nextRunAt = nextRunAt;
    }

    /**
     * 단일 진입점 — sealed {@link ScheduleSpec} + sealed {@link Targeting} 조합으로
     * 모든 캠페인 variant를 생성한다. exhaustive switch가 컴파일러 차원에서 누락 케이스 검출.
     *
     * <p>이전의 8개 정적 팩토리(immediateAll/immediateTarget/scheduledAll/...)는 본 메서드로 통합.
     */
    public static Campaign create(
            CampaignCommonInputs common,
            ScheduleSpec schedule,
            Targeting targeting
    ) {
        validateCommon(common);
        Long segmentId = applyTargeting(targeting);

        return switch (schedule) {
            case ScheduleSpec.Immediate ignored ->
                    buildOneTime(common, segmentId, SendMode.IMMEDIATE,
                            null, null, null, null, null, null, null);
            case ScheduleSpec.Scheduled scheduled -> {
                validateScheduledAt(scheduled.nextRunAt());
                yield buildOneTime(common, segmentId, SendMode.SCHEDULED,
                        null, null, null, null, null, 1, scheduled.nextRunAt());
            }
            case ScheduleSpec.Recurring recurring -> {
                validateRecurring(recurring.repeatType(),
                        recurring.recurringCount(), recurring.nextRunAt());
                String cron = RecurringCronGenerator.toCron(
                        recurring.repeatType(),
                        recurring.repeatDayOfWeek(),
                        recurring.repeatDayOfMonth(),
                        recurring.repeatTime());
                yield buildOneTime(common, segmentId, SendMode.RECURRING,
                        cron,
                        recurring.repeatType(),
                        recurring.repeatDayOfWeek(),
                        recurring.repeatDayOfMonth(),
                        recurring.repeatTime(),
                        recurring.recurringCount(),
                        recurring.nextRunAt());
            }
            case ScheduleSpec.Automation ignored ->
                    new Campaign(
                            common.projectId(),
                            common.template().messageTemplateId(),
                            segmentId,
                            common.memberId(),
                            common.campaignName(),
                            common.campaignDescription(),
                            CampaignType.AUTOMATION,
                            null,
                            common.template().channel(),
                            CampaignStatus.DRAFT,
                            null, null, null, null, null, null, null);
        };
    }

    private static Campaign buildOneTime(
            CampaignCommonInputs common,
            Long segmentId,
            SendMode sendMode,
            String recurringCron,
            RepeatType repeatType,
            String repeatDayOfWeek,
            Integer repeatDayOfMonth,
            LocalTime repeatTime,
            Integer recurringCount,
            LocalDateTime nextRunAt
    ) {
        return new Campaign(
                common.projectId(),
                common.template().messageTemplateId(),
                segmentId,
                common.memberId(),
                common.campaignName(),
                common.campaignDescription(),
                CampaignType.ONE_TIME,
                sendMode,
                common.template().channel(),
                CampaignStatus.DRAFT,
                recurringCron,
                repeatType,
                repeatDayOfWeek,
                repeatDayOfMonth,
                repeatTime,
                recurringCount,
                nextRunAt
        );
    }

    private static Long applyTargeting(Targeting targeting) {
        return switch (targeting) {
            case Targeting.All ignored -> null;
            case Targeting.Segment seg -> {
                if (seg.segmentId() == null) {
                    throw new BusinessException(CampaignErrorCode.CAMPAIGN_SEGMENT_REQUIRED);
                }
                yield seg.segmentId();
            }
        };
    }

    private static void validateCommon(CampaignCommonInputs common) {
        if (common.projectId() == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_PROJECT_REQUIRED);
        }
        if (common.template() == null || common.template().messageTemplateId() == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
        }
        if (common.memberId() == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_MEMBER_REQUIRED);
        }
        if (common.campaignName() == null || common.campaignName().isBlank()) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NAME_REQUIRED);
        }
        if (common.campaignDescription() == null || common.campaignDescription().isBlank()) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_DESCRIPTION_REQUIRED);
        }
    }

    private static void validateScheduledAt(LocalDateTime nextRunAt) {
        if (nextRunAt == null) {
            throw new BusinessException(CampaignErrorCode.SCHEDULED_AT_REQUIRED);
        }
        if (nextRunAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(CampaignErrorCode.SCHEDULED_AT_IN_PAST);
        }
    }

    private static void validateRecurring(
            RepeatType repeatType,
            Integer recurringCount,
            LocalDateTime nextRunAt
    ) {
        if (repeatType == null) {
            throw new BusinessException(CampaignErrorCode.REPEAT_TYPE_REQUIRED);
        }
        if (recurringCount == null || recurringCount < 1) {
            throw new BusinessException(CampaignErrorCode.RECURRING_COUNT_INVALID);
        }
        if (nextRunAt == null) {
            throw new BusinessException(CampaignErrorCode.SCHEDULED_AT_REQUIRED);
        }
    }

    /** 공통 입력 파라미터 묶음 (record). */
    public record CampaignCommonInputs(
            Long projectId,
            MessageTemplateSnapshot template,
            Long memberId,
            String campaignName,
            String campaignDescription
    ) {
    }

    public void validateTemplateUpdatable() {
        if (this.campaignStatus == CampaignStatus.ACTIVE) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_IS_ACTIVE);
        }
    }

    public boolean isOwnedBy(Long projectId) {
        return this.projectId.equals(projectId);
    }

    /**
     * DRAFT 상태에서 일반 필드 + 발송 대상(template/segment) 수정.
     *
     * <p>messageTemplateId 가 변경되면 channel 은 새 template 의 channel 로 함께 갱신된다 —
     * 호출자(Service)가 template lookup 후 정확한 channel 을 전달할 책임을 진다.
     * segmentId 는 nullable(Targeting.All 의미). update 시점에는 segment readiness 별도
     * 검증 안 함 — DRAFT 라서 activate 시점에 SegmentReadinessPort 가 어차피 검증.
     */
    public void update(
            String campaignName,
            String campaignDescription,
            Long messageTemplateId,
            Channel channel,
            Long segmentId
    ) {
        assertModifiable();
        validateName(campaignName);
        validateDescription(campaignDescription);
        if (messageTemplateId == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
        }
        this.campaignName = campaignName;
        this.campaignDescription = campaignDescription;
        this.messageTemplateId = messageTemplateId;
        this.channel = channel;
        this.segmentId = segmentId;
    }

    public void activate() {
        transitionTo(CampaignStatus.ACTIVE);
    }

    public void pause() {
        transitionTo(CampaignStatus.PAUSED);
    }

    public void resume() {
        transitionTo(CampaignStatus.ACTIVE);
    }

    public void complete() {
        transitionTo(CampaignStatus.ENDED);
    }

    public void transitionTo(CampaignStatus target) {
        if (!this.campaignStatus.canTransitionTo(target)) {
            throw new BusinessException(CampaignErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.campaignStatus = target;
    }

    private void assertModifiable() {
        if (this.campaignStatus != CampaignStatus.DRAFT) {
            throw new BusinessException(CampaignErrorCode.NOT_MODIFIABLE);
        }
    }

    private static void validateName(String campaignName) {
        if (campaignName == null || campaignName.isBlank()) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NAME_REQUIRED);
        }
    }

    private static void validateDescription(String campaignDescription) {
        if (campaignDescription == null || campaignDescription.isBlank()) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_DESCRIPTION_REQUIRED);
        }
    }
}
