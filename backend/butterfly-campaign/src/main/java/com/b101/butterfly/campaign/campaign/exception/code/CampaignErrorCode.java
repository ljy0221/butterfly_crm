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
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
