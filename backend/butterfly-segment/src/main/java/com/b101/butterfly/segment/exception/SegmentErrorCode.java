package com.b101.butterfly.segment.exception;

import com.b101.butterfly.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 세그먼트 도메인 에러 코드. {@code backend/docs/api/03-segment.md §7} 단일 진실 소스.
 */
public enum SegmentErrorCode implements ErrorCode {

    SEGMENT_400_NAME_LENGTH(
            "SEGMENT_400_NAME_LENGTH",
            "세그먼트 이름은 1자 이상 30자 이하여야 합니다.",
            HttpStatus.BAD_REQUEST),
    SEGMENT_400_NAME_DUPLICATED(
            "SEGMENT_400_NAME_DUPLICATED",
            "동일 프로젝트 내에 같은 이름의 세그먼트가 이미 존재합니다.",
            HttpStatus.BAD_REQUEST),
    SEGMENT_400_DESCRIPTION_LENGTH(
            "SEGMENT_400_DESCRIPTION_LENGTH",
            "세그먼트 설명은 100자 이하여야 합니다.",
            HttpStatus.BAD_REQUEST),
    SEGMENT_400_EMPTY_CONDITIONS(
            "SEGMENT_400_EMPTY_CONDITIONS",
            "조건은 1개 이상이어야 합니다.",
            HttpStatus.BAD_REQUEST),
    SEGMENT_400_INVALID_CONDITION_TREE(
            "SEGMENT_400_INVALID_CONDITION_TREE",
            "조건 트리 스키마가 올바르지 않습니다.",
            HttpStatus.BAD_REQUEST),
    SEGMENT_404_NOT_FOUND(
            "SEGMENT_404_NOT_FOUND",
            "세그먼트를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND),
    SEGMENT_409_LINKED_ACTIVE_CAMPAIGN(
            "SEGMENT_409_LINKED_ACTIVE_CAMPAIGN",
            "진행 중인 캠페인에 연결되어 수정 또는 삭제할 수 없습니다.",
            HttpStatus.CONFLICT),
    SEGMENT_409_BUILD_IN_PROGRESS(
            "SEGMENT_409_BUILD_IN_PROGRESS",
            "세그먼트 빌드가 진행 중입니다.",
            HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    SegmentErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
