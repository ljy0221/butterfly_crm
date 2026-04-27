package com.b101.butterfly.campaign.service.campaign.domain;

import com.b101.butterfly.campaign.service.campaign.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.service.campaign.domain.enums.CampaignType;
import com.b101.butterfly.campaign.service.campaign.domain.enums.Channel;
import com.b101.butterfly.campaign.service.campaign.domain.enums.RepeatType;
import com.b101.butterfly.global.entity.SoftDeletedDomain;
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
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_status", nullable = false)
    private CampaignStatus campaignStatus;

    @Column(name = "recurring_cron", nullable = false)
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

    private Campaign(
            Project project,
            MessageTemplate messageTemplate,
            Segment segment,
            Member member,
            String campaignName,
            String campaignDescription,
            CampaignType campaignType,
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
        this.segment = segment;
        this.member = member;
        this.campaignName = campaignName;
        this.campaignDescription = campaignDescription;
        this.campaignType = campaignType;
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

    public static Campaign oneTimeReccringCampaign();


    public void addExecution(CampaignExecution execution) {
        this.executions.add(execution);
        execution.assignCampaign(this);
    }
}