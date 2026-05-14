package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignExecutionStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignExecution 상태머신 (READY → SEND → SUCCESS / FAIL)")
class CampaignExecutionStateMachineTest {

    private static final Long CAMPAIGN_ID = 1L;

    private CampaignExecution ready() {
        return CampaignExecution.ready(CAMPAIGN_ID, 1, 0);
    }

    @Nested
    @DisplayName("ready (factory)")
    class Ready {
        @Test
        @DisplayName("정상 — campaignId/runRound/targetCount 검증 통과 후 READY 상태")
        void readyHappy() {
            CampaignExecution e = ready();
            assertThat(e.getCampaignExecutionStatus())
                    .isEqualTo(CampaignExecutionStatus.READY);
            assertThat(e.getCampaignId()).isEqualTo(CAMPAIGN_ID);
            assertThat(e.getRunRound()).isEqualTo(1);
        }

        @Test
        @DisplayName("campaignId null → CAMPAIGN_REQUIRED")
        void nullCampaign() {
            assertThatThrownBy(() -> CampaignExecution.ready(null, 1, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_REQUIRED);
        }

        @Test
        @DisplayName("runRound 0 → INVALID_RUN_ROUND")
        void invalidRunRound() {
            assertThatThrownBy(() -> CampaignExecution.ready(CAMPAIGN_ID, 0, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_RUN_ROUND);
        }

        @Test
        @DisplayName("targetCount -1 → INVALID_TARGET_COUNT")
        void invalidTargetCount() {
            assertThatThrownBy(() -> CampaignExecution.ready(CAMPAIGN_ID, 1, -1))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_TARGET_COUNT);
        }
    }

    @Nested
    @DisplayName("markAsSent (READY → SEND)")
    class MarkAsSent {
        @Test
        @DisplayName("READY 상태에서 SEND로 전이 + sendStartedAt 셋팅")
        void readyToSend() {
            CampaignExecution e = ready();
            e.markAsSent();
            assertThat(e.getCampaignExecutionStatus())
                    .isEqualTo(CampaignExecutionStatus.SEND);
            assertThat(e.getSendStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 SEND인 상태에서 markAsSent → INVALID_EXECUTION_TRANSITION")
        void doubleSendRejected() {
            CampaignExecution e = ready();
            e.markAsSent();
            assertThatThrownBy(e::markAsSent)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }
    }

    @Nested
    @DisplayName("markAsSuccess (SEND → SUCCESS)")
    class MarkAsSuccess {
        @Test
        @DisplayName("SEND 상태에서 SUCCESS로 전이 + sendCompletedAt 셋팅")
        void sendToSuccess() {
            CampaignExecution e = ready();
            e.markAsSent();
            e.markAsSuccess();
            assertThat(e.getCampaignExecutionStatus())
                    .isEqualTo(CampaignExecutionStatus.SUCCESS);
            assertThat(e.getSendCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("READY 상태에서 markAsSuccess → INVALID_EXECUTION_TRANSITION")
        void readyToSuccessRejected() {
            CampaignExecution e = ready();
            assertThatThrownBy(e::markAsSuccess)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }

        @Test
        @DisplayName("이미 SUCCESS인 상태에서 markAsSuccess → INVALID_EXECUTION_TRANSITION (멱등성)")
        void doubleSuccessRejected() {
            CampaignExecution e = ready();
            e.markAsSent();
            e.markAsSuccess();
            assertThatThrownBy(e::markAsSuccess)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }
    }

    @Nested
    @DisplayName("markAsFailed (SEND → FAIL)")
    class MarkAsFailed {
        @Test
        @DisplayName("SEND 상태에서 FAIL로 전이 + sendCompletedAt 셋팅")
        void sendToFail() {
            CampaignExecution e = ready();
            e.markAsSent();
            e.markAsFailed();
            assertThat(e.getCampaignExecutionStatus())
                    .isEqualTo(CampaignExecutionStatus.FAIL);
            assertThat(e.getSendCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("READY 상태에서 markAsFailed → INVALID_EXECUTION_TRANSITION")
        void readyToFailRejected() {
            CampaignExecution e = ready();
            assertThatThrownBy(e::markAsFailed)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }

        @Test
        @DisplayName("이미 FAIL → markAsFailed 거부 (1회만 허용)")
        void doubleFailRejected() {
            CampaignExecution e = ready();
            e.markAsSent();
            e.markAsFailed();
            assertThatThrownBy(e::markAsFailed)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }
    }

    @Nested
    @DisplayName("@Version optimistic locking 필드")
    class OptimisticLocking {
        @Test
        @DisplayName("version 필드는 @Version 어노테이션 + Long 타입")
        void versionFieldIsAnnotatedVersion() throws NoSuchFieldException {
            Field versionField = CampaignExecution.class.getDeclaredField("version");
            assertThat(versionField.getType()).isEqualTo(Long.class);
            assertThat(versionField.isAnnotationPresent(Version.class)).isTrue();
        }
    }

    @Nested
    @DisplayName("종착 상태 — SUCCESS/FAIL에서 어떤 전이도 거부")
    class TerminalStates {
        @Test
        @DisplayName("SUCCESS → markAsFailed 거부")
        void successToFailRejected() {
            CampaignExecution e = ready();
            e.markAsSent();
            e.markAsSuccess();
            assertThatThrownBy(e::markAsFailed)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }

        @Test
        @DisplayName("FAIL → markAsSuccess 거부")
        void failToSuccessRejected() {
            CampaignExecution e = ready();
            e.markAsSent();
            e.markAsFailed();
            assertThatThrownBy(e::markAsSuccess)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }
    }
}
