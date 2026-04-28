package com.b101.butterfly.campaign.campaign.exception.code;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CampaignErrorCode implements ErrorCode {

    CAMPAIGN_PROJECT_REQUIRED(
            "CAMPAIGN_001", "캠페인 처리 시 프로젝트 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED(
            "CAMPAIGN_002", "캠페인 처리 시 메세지 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    CAMPAIGN_MEMBER_REQUIRED(
            "CAMPAIGN_003", "캠페인 처리 시 생성자 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    CAMPAIGN_NAME_REQUIRED(
            "CAMPAIGN_004", "캠페인 처리 시 캠페인 명은 필수입니다.", HttpStatus.BAD_REQUEST),
    CAMPAIGN_DESCRIPTION_REQUIRED(
            "CAMPAIGN_005", "캠페인 처리 시 캠페인 설명 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    CAMPAIGN_SEGMENT_REQUIRED(
            "CAMPAIGN_006", "캠페인 처리 시 세그먼트 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    SCHEDULED_AT_REQUIRED(
            "CAMPAIGN_007", "예약 발송 시각은 필수입니다.", HttpStatus.BAD_REQUEST),
    SCHEDULED_AT_IN_PAST(
            "CAMPAIGN_008", "발송 예정 시각은 현재보다 과거일 수 없습니다.", HttpStatus.BAD_REQUEST),
    REPEAT_TYPE_REQUIRED(
            "CAMPAIGN_009", "반복 캠페인은 반복 유형(Repeat Type) 설정이 필수입니다.", HttpStatus.BAD_REQUEST),
    RECURRING_COUNT_INVALID(
            "CAMPAIGN_010", "목표 반복 발송 횟수는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    RECURRING_TIME_REQUIRED(
            "CAMPAIGN_011", "반복 발송 시각은 필수입니다.", HttpStatus.BAD_REQUEST),
    RECURRING_DAY_OF_WEEK_REQUIRED(
            "CAMPAIGN_012", "주간 반복 시 요일은 필수입니다.", HttpStatus.BAD_REQUEST),
    RECURRING_DAY_OF_WEEK_INVALID(
            "CAMPAIGN_013", "요일 값은 MON,TUE,WED,THU,FRI,SAT,SUN 중에서만 사용할 수 있습니다.", HttpStatus.BAD_REQUEST),
    RECURRING_DAY_OF_MONTH_INVALID(
            "CAMPAIGN_014", "월간 반복 일자는 1 이상 31 이하여야 합니다.", HttpStatus.BAD_REQUEST),
    // 캠페인 실행(CampaignExecution) 관련 에러
    CAMPAIGN_REQUIRED(
            "CAMPAIGN_015", "캠페인 실행 이력 생성 시 캠페인 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    INVALID_RUN_ROUND(
            "CAMPAIGN_016", "실행 회차는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_TARGET_COUNT(
            "CAMPAIGN_017", "발송 대상자 수는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
