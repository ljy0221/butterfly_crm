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
    PROJECT_NOT_FOUND("TEMPLATE_007", "존재하지 않는 프로젝트입니다.", HttpStatus.NOT_FOUND),
    MEMBER_NOT_FOUND("TEMPLATE_008", "존재하지 않는 멤버입니다.", HttpStatus.NOT_FOUND),
    TEMPLATE_DESCRIPTION_REQUIRED("TEMPLATE_009", "템플릿 설명은 필수입니다.", HttpStatus.BAD_REQUEST),
    TEMPLATE_TITLE_REQUIRED("TEMPLATE_010", "메세지 제목은 필수입니다.", HttpStatus.BAD_REQUEST),
    MEMBER_ID_INVALID("TEMPLATE_011", "생성자 아이디를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST),
    TEMPLATE_NOT_FOUND("TEMPLATE_012", "존재하지 않는 템플릿입니다.", HttpStatus.NOT_FOUND),
    TEMPLATE_CHANNEL_INVALID("TEMPLATE_013", "지원하지 않는 발송 채널입니다.", HttpStatus.BAD_REQUEST),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
