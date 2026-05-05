package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.lifecycle.domain.enums.RepeatType;
import com.b101.butterfly.campaign.lifecycle.domain.enums.SendMode;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 캠페인 발송 시점 사양 (Sealed VO).
 *
 * <p>{@link Campaign#create}의 단일 진입점을 통해 4가지 variant 중 하나로 선택한다.
 * exhaustive switch가 컴파일러 차원에서 누락 케이스를 검출.
 *
 * <ul>
 *   <li>{@link Immediate} — 즉시 발송 (one-time)</li>
 *   <li>{@link Scheduled} — 예약 발송 (one-time at given time)</li>
 *   <li>{@link Recurring} — 반복 발송 (cron, recurringCount회)</li>
 *   <li>{@link Automation} — 자동 발송 (이벤트 트리거)</li>
 * </ul>
 */
public sealed interface ScheduleSpec
        permits ScheduleSpec.Immediate,
                ScheduleSpec.Scheduled,
                ScheduleSpec.Recurring,
                ScheduleSpec.Automation {

    /**
     * SendMode를 결정한다 (변환은 sealed switch에서 빠지지 않게 단일 진입점).
     */
    SendMode sendMode();

    /** 즉시 발송. */
    record Immediate() implements ScheduleSpec {
        @Override
        public SendMode sendMode() {
            return SendMode.IMMEDIATE;
        }
    }

    /** 예약 발송 — 특정 시각에 1회 실행. */
    record Scheduled(LocalDateTime nextRunAt) implements ScheduleSpec {
        @Override
        public SendMode sendMode() {
            return SendMode.SCHEDULED;
        }
    }

    /** 반복 발송 — repeatTime/요일/일자 기반 cron 으로 recurringCount회 실행. */
    record Recurring(
            RepeatType repeatType,
            String repeatDayOfWeek,
            Integer repeatDayOfMonth,
            LocalTime repeatTime,
            Integer recurringCount,
            LocalDateTime nextRunAt
    ) implements ScheduleSpec {
        @Override
        public SendMode sendMode() {
            return SendMode.RECURRING;
        }
    }

    /** 자동 발송 — 이벤트 기반 (sendMode 비워둠 — Automation은 별도 CampaignType.AUTOMATION). */
    record Automation() implements ScheduleSpec {
        @Override
        public SendMode sendMode() {
            return null;
        }
    }
}
