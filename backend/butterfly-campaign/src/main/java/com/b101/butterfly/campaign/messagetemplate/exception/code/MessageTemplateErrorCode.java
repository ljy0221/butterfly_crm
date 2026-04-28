package com.b101.butterfly.campaign.messagetemplate.exception.code;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MessageTemplateErrorCode implements ErrorCode {

    TEMPLATE_PROJECT_REQUIRED("TEMPLATE_001", "템플릿 처리 시 프로젝트 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    TEMPLATE_MEMBER_REQUIRED("TEMPLATE_002", "템플릿 처리 시 회원 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    TEMPLATE_CHANNEL_REQUIRED("TEMPLATE_003", "발송 채널 정보는 필수입니다.", HttpStatus.BAD_REQUEST),
    TEMPLATE_NAME_REQUIRED("TEMPLATE_004", "템플릿 이름은 필수입니다.", HttpStatus.BAD_REQUEST),
    TEMPLATE_BODY_REQUIRED("TEMPLATE_005", "메시지 본문은 필수입니다.", HttpStatus.BAD_REQUEST),
    TEMPLATE_REQUIRED_VARIABLES_INVALID(
            "TEMPLATE_006", "템플릿 필수 변수 정보를 해석할 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
