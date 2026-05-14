package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecution;
import com.b101.butterfly.campaign.lifecycle.domain.CampaignExecutionRepository;
import com.b101.butterfly.campaign.lifecycle.domain.enums.CampaignExecutionStatus;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CampaignExecutionResultRecorder — 발송 결과 기록 진입점")
class CampaignExecutionResultRecorderTest {

    private static final Long EXECUTION_ID = 1L;
    private static final Long CAMPAIGN_ID = 10L;

    private CampaignExecutionRepository repository;
    private CampaignExecutionResultRecorder recorder;

    @BeforeEach
    void setUp() {
        repository = mock(CampaignExecutionRepository.class);
        recorder = new CampaignExecutionResultRecorder(repository);
    }

    @Nested
    @DisplayName("recordCompletion — SEND → SUCCESS")
    class RecordCompletion {

        @Test
        @DisplayName("정상: SEND 상태 실행 회차가 SUCCESS로 전이")
        void happyPath() {
            CampaignExecution execution = sentExecution();
            given(repository.findById(EXECUTION_ID)).willReturn(Optional.of(execution));

            recorder.recordCompletion(EXECUTION_ID);

            assertThat(execution.getCampaignExecutionStatus())
                    .isEqualTo(CampaignExecutionStatus.SUCCESS);
        }

        @Test
        @DisplayName("실행 회차 없음 → CAMPAIGN_NOT_FOUND")
        void notFound() {
            given(repository.findById(EXECUTION_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recorder.recordCompletion(EXECUTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }

        @Test
        @DisplayName("잘못된 전이(READY → SUCCESS) → INVALID_EXECUTION_TRANSITION 위임")
        void invalidTransition() {
            CampaignExecution readyExecution = CampaignExecution.ready(CAMPAIGN_ID, 1, 0);
            given(repository.findById(EXECUTION_ID)).willReturn(Optional.of(readyExecution));

            assertThatThrownBy(() -> recorder.recordCompletion(EXECUTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }
    }

    @Nested
    @DisplayName("recordFailure — SEND → FAIL")
    class RecordFailure {

        @Test
        @DisplayName("정상: SEND 상태 실행 회차가 FAIL로 전이")
        void happyPath() {
            CampaignExecution execution = sentExecution();
            given(repository.findById(EXECUTION_ID)).willReturn(Optional.of(execution));

            recorder.recordFailure(EXECUTION_ID);

            assertThat(execution.getCampaignExecutionStatus())
                    .isEqualTo(CampaignExecutionStatus.FAIL);
        }

        @Test
        @DisplayName("실행 회차 없음 → CAMPAIGN_NOT_FOUND")
        void notFound() {
            given(repository.findById(EXECUTION_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> recorder.recordFailure(EXECUTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }

        @Test
        @DisplayName("잘못된 전이(READY → FAIL) → INVALID_EXECUTION_TRANSITION 위임")
        void invalidTransition() {
            CampaignExecution readyExecution = CampaignExecution.ready(CAMPAIGN_ID, 1, 0);
            given(repository.findById(EXECUTION_ID)).willReturn(Optional.of(readyExecution));

            assertThatThrownBy(() -> recorder.recordFailure(EXECUTION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.INVALID_EXECUTION_TRANSITION);
        }
    }

    private CampaignExecution sentExecution() {
        CampaignExecution execution = CampaignExecution.ready(CAMPAIGN_ID, 1, 0);
        execution.markAsSent();
        return execution;
    }
}
