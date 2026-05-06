package com.b101.butterfly.campaign.lifecycle.domain;

import com.b101.butterfly.campaign.lifecycle.domain.enums.RepeatType;
import com.b101.butterfly.campaign.lifecycle.exception.code.CampaignErrorCode;
import com.b101.butterfly.global.error.BusinessException;
import java.time.LocalTime;
import java.util.Set;

public final class RecurringCronGenerator {

    private static final Set<String> ALLOWED_DAYS_OF_WEEK =
            Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private RecurringCronGenerator() {
    }

    public static String toCron(
            RepeatType repeatType,
            String repeatDayOfWeek,
            Integer repeatDayOfMonth,
            LocalTime repeatTime
    ) {
        if (repeatTime == null) {
            throw new BusinessException(CampaignErrorCode.RECURRING_TIME_REQUIRED);
        }

        int minute = repeatTime.getMinute();
        int hour = repeatTime.getHour();

        return switch (repeatType) {
            case DAILY -> String.format("0 %d %d * * *", minute, hour);
            case WEEKLY -> String.format("0 %d %d * * %s", minute, hour,
                    normalizeDayOfWeek(repeatDayOfWeek));
            case MONTHLY -> String.format("0 %d %d %d * *", minute, hour,
                    validateDayOfMonth(repeatDayOfMonth));
        };
    }

    // 월부터 일 검증+반복 요일 복수 선택 가능
    private static String normalizeDayOfWeek(String repeatDayOfWeek) {
        if (repeatDayOfWeek == null || repeatDayOfWeek.isBlank()) {
            throw new BusinessException(CampaignErrorCode.RECURRING_DAY_OF_WEEK_REQUIRED);
        }
        String[] tokens = repeatDayOfWeek.split(",");
        StringBuilder normalized = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim().toUpperCase();
            if (!ALLOWED_DAYS_OF_WEEK.contains(token)) {
                throw new BusinessException(CampaignErrorCode.RECURRING_DAY_OF_WEEK_INVALID);
            }
            if (i > 0) {
                normalized.append(',');
            }
            normalized.append(token);
        }
        return normalized.toString();
    }

    // 1-31 검증
    private static int validateDayOfMonth(Integer repeatDayOfMonth) {
        if (repeatDayOfMonth == null || repeatDayOfMonth < 1 || repeatDayOfMonth > 31) {
            throw new BusinessException(CampaignErrorCode.RECURRING_DAY_OF_MONTH_INVALID);
        }
        return repeatDayOfMonth;
    }
}
