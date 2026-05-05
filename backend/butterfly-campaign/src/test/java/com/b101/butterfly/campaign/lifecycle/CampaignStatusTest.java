package com.b101.butterfly.campaign.campaign;

import static org.assertj.core.api.Assertions.assertThat;

import com.b101.butterfly.campaign.campaign.domain.enums.CampaignStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CampaignStatus state machine")
class CampaignStatusTest {

    @ParameterizedTest(name = "{0} → {1} 는 {2} 이어야 한다")
    @CsvSource(textBlock = """
            DRAFT,  ACTIVE, true
            DRAFT,  PAUSED, false
            DRAFT,  ENDED,  false
            DRAFT,  DRAFT,  false
            ACTIVE, PAUSED, true
            ACTIVE, ENDED,  true
            ACTIVE, DRAFT,  false
            ACTIVE, ACTIVE, false
            PAUSED, ACTIVE, true
            PAUSED, ENDED,  true
            PAUSED, DRAFT,  false
            PAUSED, PAUSED, false
            ENDED,  DRAFT,  false
            ENDED,  ACTIVE, false
            ENDED,  PAUSED, false
            ENDED,  ENDED,  false
            """)
    void transitionMatrix(CampaignStatus from, CampaignStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} 의 다음 허용 상태")
    @CsvSource({
            "DRAFT,  ACTIVE",
            "ACTIVE, PAUSED|ENDED",
            "PAUSED, ACTIVE|ENDED",
            "ENDED,  "
    })
    @DisplayName("nextAllowed()는 명시적으로 허용된 상태만 반환한다")
    void nextAllowedReflectsTransitionMatrix(CampaignStatus status, String expectedCsv) {
        if (expectedCsv == null || expectedCsv.isBlank()) {
            assertThat(status.nextAllowed()).isEmpty();
            return;
        }
        for (String name : expectedCsv.split("\\|")) {
            assertThat(status.nextAllowed()).contains(CampaignStatus.valueOf(name));
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("ENDED는 종착 상태 — 어떤 전이도 허용 안 함")
    void endedIsTerminal() {
        assertThat(CampaignStatus.ENDED.nextAllowed()).isEmpty();
    }
}
