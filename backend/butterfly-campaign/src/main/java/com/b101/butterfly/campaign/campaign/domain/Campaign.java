package com.b101.butterfly.campaign.campaign.domain;

import com.b101.butterfly.auth.entity.Member;
import com.b101.butterfly.auth.entity.Project;
import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.campaign.domain.enums.CampaignType;
import com.b101.butterfly.campaign.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.campaign.domain.enums.RepeatType;
import com.b101.butterfly.campaign.campaign.domain.enums.SendMode;
import com.b101.butterfly.campaign.campaign.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.messagetemplate.domain.MessageTemplate;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.entity.Segment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_template_id", nullable = false)
    private MessageTemplate messageTemplate;

    // TODO: Segment 모듈 추가되면 외래키 참조로 변경
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private Segment segment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

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

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CampaignExecution> executions;

    // TODO 세그먼트 추가 시 매개변수 타입 변경
    private Campaign(
            Project project,
            MessageTemplate messageTemplate,
            Segment segment,
            Member member,
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
        this.project = project;
        this.messageTemplate = messageTemplate;
        // TODO: 세그먼트 추가 시 변경
        this.segment = segment;
        this.member = member;
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
        this.executions = new ArrayList<>();
    }

    // 즉시+전체
    public static Campaign immediateAll(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        return new Campaign(
                project,
                messageTemplate,
                null,
                member,
                campaignName,
                campaignDescription,
                CampaignType.ONE_TIME,
                SendMode.IMMEDIATE,
                messageTemplate.getChannel(),
                CampaignStatus.DRAFT,
                null, null, null, null, null, null, null
        );
    }

    // 즉시+타겟
    public static Campaign immediateTarget(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription,
            Segment segment
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        validateSegment(segment);
        return new Campaign(
                project,
                messageTemplate,
                segment,
                member,
                campaignName,
                campaignDescription,
                CampaignType.ONE_TIME,
                SendMode.IMMEDIATE,
                messageTemplate.getChannel(),
                CampaignStatus.DRAFT,
                null, null, null, null, null, null, null
        );
    }

    // 예약+전체
    public static Campaign scheduledAll(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription,
            LocalDateTime nextRunAt
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        validateScheduledAt(nextRunAt);
        return new Campaign(
                project, messageTemplate, null, member,
                campaignName, campaignDescription,
                CampaignType.ONE_TIME, SendMode.SCHEDULED,
                messageTemplate.getChannel(), CampaignStatus.DRAFT,
                null, null, null, null, null,
                1,
                nextRunAt
        );
    }

    // 예약+타겟
    public static Campaign scheduledTarget(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription,
            Segment segment,
            LocalDateTime nextRunAt
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        validateSegment(segment);
        validateScheduledAt(nextRunAt);
        return new Campaign(
                project,
                messageTemplate,
                segment,
                member,
                campaignName,
                campaignDescription,
                CampaignType.ONE_TIME,
                SendMode.SCHEDULED,
                messageTemplate.getChannel(),
                CampaignStatus.DRAFT,
                null, null, null, null, null,
                1,
                nextRunAt
        );
    }

    // 반복+전체
    public static Campaign recurringAll(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription,
            RepeatType repeatType,
            String repeatDayOfWeek,
            Integer repeatDayOfMonth,
            LocalTime repeatTime,
            Integer recurringCount,
            LocalDateTime nextRunAt
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        validateRecurring(repeatType, recurringCount, nextRunAt);
        String recurringCron = RecurringCronGenerator.toCron(
                repeatType, repeatDayOfWeek, repeatDayOfMonth, repeatTime);
        return new Campaign(
                project,
                messageTemplate,
                null,
                member,
                campaignName,
                campaignDescription,
                CampaignType.ONE_TIME,
                SendMode.RECURRING,
                messageTemplate.getChannel(),
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

    // 반복+타겟
    public static Campaign recurringTarget(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription,
            Segment segment,
            RepeatType repeatType,
            String repeatDayOfWeek,
            Integer repeatDayOfMonth,
            LocalTime repeatTime,
            Integer recurringCount,
            LocalDateTime nextRunAt
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        validateSegment(segment);
        validateRecurring(repeatType, recurringCount, nextRunAt);
        String recurringCron = RecurringCronGenerator.toCron(
                repeatType, repeatDayOfWeek, repeatDayOfMonth, repeatTime);
        return new Campaign(
                project,
                messageTemplate,
                segment,
                member,
                campaignName,
                campaignDescription,
                CampaignType.ONE_TIME,
                SendMode.RECURRING,
                messageTemplate.getChannel(),
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

    // 자동+전체
    public static Campaign automationAll(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        return new Campaign(
                project, messageTemplate, null, member,
                campaignName, campaignDescription,
                CampaignType.AUTOMATION, null,
                messageTemplate.getChannel(), CampaignStatus.DRAFT,
                null, null, null, null, null, null, null
        );
    }

    // 자동+타겟
    public static Campaign automationTarget(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription,
            Segment segment
    ) {
        validateCommon(project, messageTemplate, member, campaignName, campaignDescription);
        validateSegment(segment);
        return new Campaign(
                project, messageTemplate, segment, member,
                campaignName, campaignDescription,
                CampaignType.AUTOMATION, null,
                messageTemplate.getChannel(), CampaignStatus.DRAFT,
                null, null, null, null, null, null, null
        );
    }

    private static void validateCommon(
            Project project,
            MessageTemplate messageTemplate,
            Member member,
            String campaignName,
            String campaignDescription
    ) {
        if (project == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_PROJECT_REQUIRED);
        }
        if (messageTemplate == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED);
        }
        if (member == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_MEMBER_REQUIRED);
        }
        if (campaignName == null || campaignName.isBlank()) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_NAME_REQUIRED);
        }
        if (campaignDescription == null || campaignDescription.isBlank()) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_DESCRIPTION_REQUIRED);
        }
    }

    // TODO segment 모듈 추가 시 파라미터 변경
    private static void validateSegment(Segment segment) {
        if (segment == null) {
            throw new BusinessException(CampaignErrorCode.CAMPAIGN_SEGMENT_REQUIRED);
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

    public void addExecution(CampaignExecution execution) {
        this.executions.add(execution);
        execution.assignCampaign(this);
    }
}
