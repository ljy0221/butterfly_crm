package com.b101.butterfly.segment.domain.condition.validation;

import com.b101.butterfly.global.error.BusinessException;
import com.b101.butterfly.segment.domain.condition.Period;
import com.b101.butterfly.segment.exception.SegmentErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTreeValidatorPeriodTest {

    private final PeriodValidator validator = new PeriodValidator();

    @Test
    @DisplayName("Relative.days 양수는 통과한다")
    void relativeDaysPositiveAccepted() {
        assertThatCode(() -> validator.validate(new Period.Relative(1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(new Period.Relative(30)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Relative.days <= 0은 SEGMENT_400_INVALID_CONDITION_TREE로 거절된다")
    void relativeDaysNonPositiveRejected() {
        assertInvalid(() -> validator.validate(new Period.Relative(0)));
        assertInvalid(() -> validator.validate(new Period.Relative(-1)));
    }

    @Test
    @DisplayName("Absolute startDate <= endDate는 통과한다 (동일 일자 포함)")
    void absoluteValidRangeAccepted() {
        assertThatCode(
                        () ->
                                validator.validate(
                                        new Period.Absolute(
                                                LocalDate.of(2026, 1, 1),
                                                LocalDate.of(2026, 1, 31))))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                validator.validate(
                                        new Period.Absolute(
                                                LocalDate.of(2026, 1, 1),
                                                LocalDate.of(2026, 1, 1))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Absolute startDate > endDate는 거절된다")
    void absoluteReversedRangeRejected() {
        assertInvalid(
                () ->
                        validator.validate(
                                new Period.Absolute(
                                        LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))));
    }

    @Test
    @DisplayName("Absolute의 startDate / endDate가 null이면 거절된다")
    void absoluteNullDatesRejected() {
        assertInvalid(
                () ->
                        validator.validate(
                                new Period.Absolute(null, LocalDate.of(2026, 1, 1))));
        assertInvalid(
                () ->
                        validator.validate(
                                new Period.Absolute(LocalDate.of(2026, 1, 1), null)));
        assertInvalid(() -> validator.validate(new Period.Absolute(null, null)));
    }

    @Test
    @DisplayName("Cumulative는 추가 검증 없이 통과한다")
    void cumulativeAlwaysAccepted() {
        assertThatCode(() -> validator.validate(new Period.Cumulative()))
                .doesNotThrowAnyException();
    }

    private static void assertInvalid(ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(SegmentErrorCode.SEGMENT_400_INVALID_CONDITION_TREE));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Throwable;
    }
}
