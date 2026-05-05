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
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
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
    private CampaignActivator activator;

    @BeforeEach
    void setUp() {
        campaignRepository = mock(CampaignRepository.class);
        campaignExecutionRepository = mock(CampaignExecutionRepository.class);
        publisher = mock(CampaignSendInstructionPublisher.class);
        Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        activator = new CampaignActivator(
                campaignRepository, campaignExecutionRepository, publisher, fixedClock);
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

    private Campaign draftCampaign() {
        MessageTemplateSnapshot template =
                new MessageTemplateSnapshot(TEMPLATE_ID, PROJECT_ID, Channel.PUSH);
        Campaign c = Campaign.immediateAll(PROJECT_ID, template, MEMBER_ID, "원본", "원본설명");
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
