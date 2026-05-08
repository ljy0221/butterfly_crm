package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.lifecycle.domain.Campaign;
import com.b101.butterfly.campaign.lifecycle.application.CampaignActivator;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionRepository;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignExecutionStatus;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignStatus;
import com.b101.butterfly.campaign.messagetemplate.domain.Channel;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstruction;
import com.b101.butterfly.campaign.lifecycle.port.CampaignSendInstructionPublisher;
import com.b101.butterfly.campaign.lifecycle.port.SegmentReadinessPort;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.campaign.lifecycle.domain.ScheduleSpec;
import com.b101.butterfly.campaign.lifecycle.domain.Targeting;
import com.b101.butterfly.campaign.messagetemplate.port.MessageTemplateSnapshot;
import com.b101.butterfly.global.error.BusinessException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("CampaignActivator")
class CampaignActivatorTest {

    private static final Long CAMPAIGN_ID = 300L;
    private static final Long EXECUTION_ID = 400L;
    private static final Long PROJECT_ID = 1L;
    private static final Long MEMBER_ID = 10L;
    private static final Long TEMPLATE_ID = 100L;
    private static final Instant FIXED_NOW = Instant.parse("2026-05-02T12:00:00Z");

    private CampaignRepository campaignRepository;
    private CampaignExecutionRepository campaignExecutionRepository;
    private CampaignSendInstructionPublisher publisher;
    private SegmentReadinessPort segmentReadinessPort;
    private CampaignActivator activator;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        campaignExecutionRepository = mock(CampaignExecutionRepository.class);
        publisher = mock(CampaignSendInstructionPublisher.class);
        segmentReadinessPort = mock(SegmentReadinessPort.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        activator = new CampaignActivator(
                campaignRepository, campaignExecutionRepository, publisher,
                segmentReadinessPort, fixedClock);
    }

    @Test
    @DisplayName("DRAFT → ACTIVE + Execution 생성 + Kafka publish + Execution=SEND")
    void activateHappyPath() {
        Campaign campaign = draftCampaign();
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(campaignRepository.saveAndFlush(any(Campaign.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(campaignExecutionRepository.saveAndFlush(any(CampaignExecution.class)))
                .willAnswer(inv -> {
                    CampaignExecution saved = inv.getArgument(0);
                    setField(saved, "campaignExecutionId", EXECUTION_ID);
                    return saved;
                });

        Long returnedExecutionId = activator.activate(CAMPAIGN_ID, PROJECT_ID);

        assertThat(returnedExecutionId).isEqualTo(EXECUTION_ID);
        assertThat(campaign.getCampaignStatus()).isEqualTo(CampaignStatus.ACTIVE);

        ArgumentCaptor<CampaignExecution> execCaptor =
                ArgumentCaptor.forClass(CampaignExecution.class);
        verify(campaignExecutionRepository, times(1)).saveAndFlush(execCaptor.capture());
        CampaignExecution execution = execCaptor.getValue();
        assertThat(execution.getCampaignId()).isEqualTo(CAMPAIGN_ID);
        assertThat(execution.getRunRound()).isEqualTo(1);
        assertThat(execution.getCampaignExecutionStatus()).isEqualTo(CampaignExecutionStatus.SEND);
        assertThat(execution.getSendStartedAt()).isNotNull();

        ArgumentCaptor<CampaignSendInstruction> captor =
                ArgumentCaptor.forClass(CampaignSendInstruction.class);
        verify(publisher, times(1)).publish(captor.capture());
        CampaignSendInstruction instr = captor.getValue();
        assertThat(instr.executionId()).isEqualTo(EXECUTION_ID);
        assertThat(instr.campaignId()).isEqualTo(CAMPAIGN_ID);
        assertThat(instr.projectId()).isEqualTo(PROJECT_ID);
        assertThat(instr.templateId()).isEqualTo(TEMPLATE_ID);
        assertThat(instr.segmentId()).isNull();  // immediateAll
        assertThat(instr.channel()).isEqualTo(Channel.PUSH);
        assertThat(instr.idempotencyKey()).isEqualTo("exec-" + EXECUTION_ID);
        assertThat(instr.publishedAt()).isEqualTo(FIXED_NOW);
        assertThat(instr.schemaVersion()).isEqualTo(CampaignSendInstruction.CURRENT_SCHEMA_VERSION);
    }

    @Test
    @DisplayName("다른 프로젝트의 캠페인 활성화 시 CAMPAIGN_NOT_FOUND")
    void crossProjectRejected() {
        Campaign campaign = draftCampaign();
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        assertThatThrownBy(() -> activator.activate(CAMPAIGN_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("이미 ACTIVE인 캠페인은 INVALID_STATUS_TRANSITION")
    void alreadyActiveRejected() {
        Campaign campaign = draftCampaign();
        campaign.activate();  // → ACTIVE
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);

        assertThatThrownBy(() -> activator.activate(CAMPAIGN_ID, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.INVALID_STATUS_TRANSITION);

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Targeting.All (segmentId=null) — segment 검증 skip, 정상 활성화")
    void activate_with_targeting_all_skips_segment_validation() {
        Campaign campaign = draftCampaign(); // segmentId = null
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(campaignRepository.saveAndFlush(any(Campaign.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(campaignExecutionRepository.saveAndFlush(any(CampaignExecution.class)))
                .willAnswer(inv -> {
                    CampaignExecution saved = inv.getArgument(0);
                    setField(saved, "campaignExecutionId", EXECUTION_ID);
                    return saved;
                });

        activator.activate(CAMPAIGN_ID, PROJECT_ID);

        // segmentReadinessPort.requireReady가 호출되지 않아야 한다
        verify(segmentReadinessPort, never()).requireReady(any());
        assertThat(campaign.getCampaignStatus()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    @DisplayName("Targeting.Segment + buildStatus=READY — 정상 활성화")
    void activate_with_segment_READY_proceeds() {
        Campaign campaign = draftCampaignWithSegment(200L);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        given(campaignRepository.saveAndFlush(any(Campaign.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(campaignExecutionRepository.saveAndFlush(any(CampaignExecution.class)))
                .willAnswer(inv -> {
                    CampaignExecution saved = inv.getArgument(0);
                    setField(saved, "campaignExecutionId", EXECUTION_ID);
                    return saved;
                });
        // requireReady는 READY일 때 아무 예외도 던지지 않음 (정상 동작)

        Long executionId = activator.activate(CAMPAIGN_ID, PROJECT_ID);

        verify(segmentReadinessPort, times(1)).requireReady(200L);
        assertThat(executionId).isEqualTo(EXECUTION_ID);
        assertThat(campaign.getCampaignStatus()).isEqualTo(CampaignStatus.ACTIVE);
    }

    @Test
    @DisplayName("Targeting.Segment + buildStatus=BUILDING — SEGMENT_NOT_READY 예외")
    void activate_with_segment_BUILDING_throws_NOT_READY() {
        Campaign campaign = draftCampaignWithSegment(200L);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        org.mockito.Mockito.doThrow(new BusinessException(CampaignErrorCode.SEGMENT_NOT_READY))
                .when(segmentReadinessPort).requireReady(200L);

        assertThatThrownBy(() -> activator.activate(CAMPAIGN_ID, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.SEGMENT_NOT_READY);

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Targeting.Segment + buildStatus=PENDING — SEGMENT_NOT_READY 예외")
    void activate_with_segment_PENDING_throws_NOT_READY() {
        Campaign campaign = draftCampaignWithSegment(200L);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        org.mockito.Mockito.doThrow(new BusinessException(CampaignErrorCode.SEGMENT_NOT_READY))
                .when(segmentReadinessPort).requireReady(200L);

        assertThatThrownBy(() -> activator.activate(CAMPAIGN_ID, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.SEGMENT_NOT_READY);

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Targeting.Segment + buildStatus=FAILED — SEGMENT_NOT_READY 예외")
    void activate_with_segment_FAILED_throws_NOT_READY() {
        Campaign campaign = draftCampaignWithSegment(200L);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        org.mockito.Mockito.doThrow(new BusinessException(CampaignErrorCode.SEGMENT_NOT_READY))
                .when(segmentReadinessPort).requireReady(200L);

        assertThatThrownBy(() -> activator.activate(CAMPAIGN_ID, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.SEGMENT_NOT_READY);

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("Targeting.Segment + segment 미존재 — SEGMENT_NOT_FOUND 예외")
    void activate_with_segment_NOT_FOUND_throws() {
        Campaign campaign = draftCampaignWithSegment(200L);
        given(campaignRepository.getByCampaignId(CAMPAIGN_ID)).willReturn(campaign);
        org.mockito.Mockito.doThrow(new BusinessException(CampaignErrorCode.SEGMENT_NOT_FOUND))
                .when(segmentReadinessPort).requireReady(200L);

        assertThatThrownBy(() -> activator.activate(CAMPAIGN_ID, PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CampaignErrorCode.SEGMENT_NOT_FOUND);

        verify(publisher, never()).publish(any());
    }

    private Campaign draftCampaign() {
        MessageTemplateSnapshot template =
                new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH);
        Campaign.CampaignCommonInputs common = new Campaign.CampaignCommonInputs(
                PROJECT_ID, template, MEMBER_ID, "원본", "원본설명");
        Campaign c = Campaign.create(common, new ScheduleSpec.Immediate(), new Targeting.All());
        setField(c, "campaignId", CAMPAIGN_ID);
        return c;
    }

    /** Targeting.Segment 캠페인 팩토리 — segmentId를 가진다. */
    private Campaign draftCampaignWithSegment(Long segmentId) {
        MessageTemplateSnapshot template =
                new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH);
        Campaign.CampaignCommonInputs common = new Campaign.CampaignCommonInputs(
                PROJECT_ID, template, MEMBER_ID, "세그먼트 캠페인", "설명");
        Campaign c = Campaign.create(
                common, new ScheduleSpec.Immediate(), new Targeting.Segment(segmentId));
        setField(c, "campaignId", CAMPAIGN_ID);
        return c;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = findField(target.getClass(), name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field findField(Class<?> c, String n) throws NoSuchFieldException {
        Class<?> cur = c;
        while (cur != null) {
            try { return cur.getDeclaredField(n); } catch (NoSuchFieldException ignored) { cur = cur.getSuperclass(); }
        }
        throw new NoSuchFieldException(n);
    }
}
