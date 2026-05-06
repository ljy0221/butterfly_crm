package com.b101.butterfly.campaign.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b101.butterfly.campaign.lifecycle.domain.RecurringCronGenerator;
import com.b101.butterfly.campaign.lifecycle.domain.enums.RepeatType;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecurringCronGeneratorTest {

    @Nested
    @DisplayName("toCron() - DAILY")
    class Daily {

        @Test
        @DisplayName("DAILY는 매일 지정된 시·분에 실행되는 cron으로 변환된다")
        void dailyAtSpecificTime() {
            String cron = RecurringCronGenerator.toCron(
                    RepeatType.DAILY, null, null, LocalTime.of(14, 30));

            assertThat(cron).isEqualTo("0 30 14 * * *");
        }

        @Test
        @DisplayName("DAILY는 자정(00:00)도 정상적으로 변환된다")
        void dailyAtMidnight() {
            String cron = RecurringCronGenerator.toCron(
                    RepeatType.DAILY, null, null, LocalTime.of(0, 0));

            assertThat(cron).isEqualTo("0 0 0 * * *");
        }
    }

    @Nested
    @DisplayName("toCron() - WEEKLY")
    class Weekly {

        @Test
        @DisplayName("WEEKLY 단일 요일은 해당 요일 cron으로 변환된다")
        void weeklySingleDay() {
            String cron = RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, "MON", null, LocalTime.of(9, 0));

            assertThat(cron).isEqualTo("0 0 9 * * MON");
        }

        @Test
        @DisplayName("WEEKLY 복수 요일은 콤마로 구분되어 cron으로 변환된다")
        void weeklyMultipleDays() {
            String cron = RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, "MON,TUE,FRI", null, LocalTime.of(9, 0));

            assertThat(cron).isEqualTo("0 0 9 * * MON,TUE,FRI");
        }

        @Test
        @DisplayName("WEEKLY 요일은 소문자/공백이 섞여도 정규화된다")
        void weeklyMixedCaseAndSpace() {
            String cron = RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, "mon, tue ,wed", null, LocalTime.of(9, 0));

            assertThat(cron).isEqualTo("0 0 9 * * MON,TUE,WED");
        }

        @Test
        @DisplayName("WEEKLY에서 요일이 null이면 RECURRING_DAY_OF_WEEK_REQUIRED 예외가 발생한다")
        void weeklyNullDayThrows() {
            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, null, null, LocalTime.of(9, 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_DAY_OF_WEEK_REQUIRED);
        }

        @Test
        @DisplayName("WEEKLY에서 요일이 공백이면 RECURRING_DAY_OF_WEEK_REQUIRED 예외가 발생한다")
        void weeklyBlankDayThrows() {
            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, "   ", null, LocalTime.of(9, 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_DAY_OF_WEEK_REQUIRED);
        }

        @Test
        @DisplayName("WEEKLY에서 허용되지 않은 요일 토큰이면 RECURRING_DAY_OF_WEEK_INVALID 예외가 발생한다")
        void weeklyInvalidDayThrows() {
            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, "FOO", null, LocalTime.of(9, 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_DAY_OF_WEEK_INVALID);
        }

        @Test
        @DisplayName("WEEKLY 복수 요일 중 하나라도 잘못되면 RECURRING_DAY_OF_WEEK_INVALID 예외가 발생한다")
        void weeklyMixedInvalidDayThrows() {
            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, "MON,FOO", null, LocalTime.of(9, 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_DAY_OF_WEEK_INVALID);
        }
    }

    @Nested
    @DisplayName("toCron() - MONTHLY")
    class Monthly {

        @Test
        @DisplayName("MONTHLY는 매달 지정된 일자에 실행되는 cron으로 변환된다")
        void monthlyAtSpecificDay() {
            String cron = RecurringCronGenerator.toCron(
                    RepeatType.MONTHLY, null, 15, LocalTime.of(0, 0));

            assertThat(cron).isEqualTo("0 0 0 15 * *");
        }

        @Test
        @DisplayName("MONTHLY 일자 경계값(1, 31)도 정상적으로 변환된다")
        void monthlyBoundaryDays() {
            assertThat(RecurringCronGenerator.toCron(
                    RepeatType.MONTHLY, null, 1, LocalTime.of(8, 0)))
                    .isEqualTo("0 0 8 1 * *");

            assertThat(RecurringCronGenerator.toCron(
                    RepeatType.MONTHLY, null, 31, LocalTime.of(23, 59)))
                    .isEqualTo("0 59 23 31 * *");
        }

        @Test
        @DisplayName("MONTHLY 일자가 null이면 RECURRING_DAY_OF_MONTH_INVALID 예외가 발생한다")
        void monthlyNullDayThrows() {
            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.MONTHLY, null, null, LocalTime.of(0, 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_DAY_OF_MONTH_INVALID);
        }

        @Test
        @DisplayName("MONTHLY 일자가 0이거나 32 이상이면 RECURRING_DAY_OF_MONTH_INVALID 예외가 발생한다")
        void monthlyOutOfRangeThrows() {
            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.MONTHLY, null, 0, LocalTime.of(0, 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_DAY_OF_MONTH_INVALID);

            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.MONTHLY, null, 32, LocalTime.of(0, 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_DAY_OF_MONTH_INVALID);
        }
    }

    @Nested
    @DisplayName("toCron() - 공통")
    class Common {

        @Test
        @DisplayName("repeatTime이 null이면 모든 RepeatType에서 RECURRING_TIME_REQUIRED 예외가 발생한다")
        void nullTimeThrowsForAllTypes() {
            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.DAILY, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_TIME_REQUIRED);

            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.WEEKLY, "MON", null, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_TIME_REQUIRED);

            assertThatThrownBy(() -> RecurringCronGenerator.toCron(
                    RepeatType.MONTHLY, null, 10, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CampaignErrorCode.RECURRING_TIME_REQUIRED);
        }
    }
}
