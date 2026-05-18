package com.b101.butterfly.worker.campaign;

import static org.mockito.Mockito.verify;

import com.b101.butterfly.campaign.lifecycle.application.CampaignExecutionResultRecorder;
import com.b101.butterfly.campaign.lifecycle.port.CampaignExecutionResult;
import com.b101.butterfly.payment.application.CreditAccountService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignExecutionBillingServiceTest {

    @Mock private CreditAccountService creditAccountService;
    @Mock private CampaignExecutionResultRecorder campaignExecutionResultRecorder;

    private CampaignExecutionBillingService service;

    @BeforeEach
    void setUp() {
        service = new CampaignExecutionBillingService(
                creditAccountService,
                campaignExecutionResultRecorder
        );
    }

    @Test
    void settleAndRecord_는_성공_발송건을_정산하고_SUCCESS로_전이한다() {
        CampaignExecutionResult result =
                CampaignExecutionResult.of(10L, 20L, 30L, 3, 1, Instant.now());

        service.settleAndRecord(result);

        verify(creditAccountService).settleCampaignExecution(10L, 3);
        verify(campaignExecutionResultRecorder).recordCompletion(10L);
    }

    @Test
    void settleAndRecord_는_sentCount_0이면_예약을_해제하고_FAIL로_전이한다() {
        CampaignExecutionResult result =
                CampaignExecutionResult.of(10L, 20L, 30L, 0, 4, Instant.now());

        service.settleAndRecord(result);

        verify(creditAccountService).settleCampaignExecution(10L, 0);
        verify(campaignExecutionResultRecorder).recordFailure(10L);
    }
}
