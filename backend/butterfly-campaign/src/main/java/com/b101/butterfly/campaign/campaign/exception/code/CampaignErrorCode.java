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
    RECURRING_CRON_REQUIRED(
            "CAMPAIGN_009", "반복 캠페인은 크론(Cron) 표현식이 필수입니다.", HttpStatus.BAD_REQUEST),
    REPEAT_TYPE_REQUIRED(
            "CAMPAIGN_010", "반복 캠페인은 반복 유형(Repeat Type) 설정이 필수입니다.", HttpStatus.BAD_REQUEST),
    RECURRING_COUNT_INVALID(
            "CAMPAIGN_011", "목표 반복 발송 횟수는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
