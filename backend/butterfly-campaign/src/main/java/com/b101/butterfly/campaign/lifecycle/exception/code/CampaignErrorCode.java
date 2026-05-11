package com.b101.butterfly.campaign.lifecycle.exception.code;

import com.b101.butterfly.global.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * lifecycle BC의 도메인 에러 코드.
 *
 * <p>카테고리:
 * <ul>
 *   <li><b>CAMPAIGN_001 ~ CAMPAIGN_014</b> — 캠페인 생성/스케줄 입력 검증 (CAMPAIGN_*)</li>
 *   <li><b>CAMPAIGN_015 ~ CAMPAIGN_017</b> — 캠페인 실행(CampaignExecution) 생성 입력 검증 (EXECUTION_*)</li>
 *   <li><b>CAMPAIGN_018 ~ CAMPAIGN_021</b> — 캠페인 라이프사이클/상태 전이 (CAMPAIGN_*)</li>
 *   <li><b>CAMPAIGN_022</b> — 발송 상태 전이 (EXECUTION_*)</li>
 * </ul>
 *
 * <p>코드 prefix는 도메인 식별 유지(CAMPAIGN_XXX)이며, JavaDoc/주석으로 카테고리를 명시한다.
 */
@Getter
@RequiredArgsConstructor
public enum CampaignErrorCode implements ErrorCode {

    // ==================== 캠페인 입력 검증 (CAMPAIGN_001 ~ CAMPAIGN_014) ====================

    /** 캠페인 처리 시 프로젝트 정보 누락. */
    CAMPAIGN_PROJECT_REQUIRED(
            "CAMPAIGN_001", "캠페인 처리 시 프로젝트 정보는 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 캠페인 처리 시 메세지 템플릿 정보 누락. */
    CAMPAIGN_MESSAGE_TEMPLATE_REQUIRED(
            "CAMPAIGN_002", "캠페인 처리 시 메세지 정보는 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 캠페인 처리 시 생성자(member) 정보 누락. */
    CAMPAIGN_MEMBER_REQUIRED(
            "CAMPAIGN_003", "캠페인 처리 시 생성자 정보는 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 캠페인 명 누락 또는 빈 값. */
    CAMPAIGN_NAME_REQUIRED(
            "CAMPAIGN_004", "캠페인 처리 시 캠페인 명은 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 캠페인 설명 누락 또는 빈 값. */
    CAMPAIGN_DESCRIPTION_REQUIRED(
            "CAMPAIGN_005", "캠페인 처리 시 캠페인 설명 정보는 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 세그먼트 타겟인데 segmentId 누락. */
    CAMPAIGN_SEGMENT_REQUIRED(
            "CAMPAIGN_006", "캠페인 처리 시 세그먼트 정보는 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 예약 발송 시각 누락. */
    SCHEDULED_AT_REQUIRED(
            "CAMPAIGN_007", "예약 발송 시각은 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 예약 발송 시각이 과거. */
    SCHEDULED_AT_IN_PAST(
            "CAMPAIGN_008", "발송 예정 시각은 현재보다 과거일 수 없습니다.", HttpStatus.BAD_REQUEST),

    /** 반복 캠페인의 RepeatType 누락. */
    REPEAT_TYPE_REQUIRED(
            "CAMPAIGN_009", "반복 캠페인은 반복 유형(Repeat Type) 설정이 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 반복 발송 횟수가 1 미만. */
    RECURRING_COUNT_INVALID(
            "CAMPAIGN_010", "목표 반복 발송 횟수는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

    /** 반복 발송 시각(LocalTime) 누락. */
    RECURRING_TIME_REQUIRED(
            "CAMPAIGN_011", "반복 발송 시각은 필수입니다.", HttpStatus.BAD_REQUEST),

    /** WEEKLY 반복인데 요일 누락. */
    RECURRING_DAY_OF_WEEK_REQUIRED(
            "CAMPAIGN_012", "주간 반복 시 요일은 필수입니다.", HttpStatus.BAD_REQUEST),

    /** 요일 값이 MON-SUN 외. */
    RECURRING_DAY_OF_WEEK_INVALID(
            "CAMPAIGN_013", "요일 값은 MON,TUE,WED,THU,FRI,SAT,SUN 중에서만 사용할 수 있습니다.",
            HttpStatus.BAD_REQUEST),

    /** MONTHLY 반복 일자가 1-31 범위 밖. */
    RECURRING_DAY_OF_MONTH_INVALID(
            "CAMPAIGN_014", "월간 반복 일자는 1 이상 31 이하여야 합니다.", HttpStatus.BAD_REQUEST),

    // ==================== 캠페인 실행 입력 검증 (CAMPAIGN_015 ~ CAMPAIGN_017) ====================

    /** CampaignExecution 생성 시 campaignId 누락. */
    CAMPAIGN_REQUIRED(
            "CAMPAIGN_015", "캠페인 실행 이력 생성 시 캠페인 정보는 필수입니다.", HttpStatus.BAD_REQUEST),

    /** CampaignExecution 회차가 1 미만. */
    INVALID_RUN_ROUND(
            "CAMPAIGN_016", "실행 회차는 1 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

    /** CampaignExecution 발송 대상자 수가 음수. */
    INVALID_TARGET_COUNT(
            "CAMPAIGN_017", "발송 대상자 수는 0 이상이어야 합니다.", HttpStatus.BAD_REQUEST),

    // ==================== 캠페인 라이프사이클/상태 전이 (CAMPAIGN_018 ~ CAMPAIGN_021) ====================

    /** 활성(ACTIVE) 캠페인에 사용 중인 템플릿을 수정/삭제하려는 시도. */
    CAMPAIGN_IS_ACTIVE(
            "CAMPAIGN_018", "현재 활성화된 캠페인입니다.", HttpStatus.CONFLICT),

    /** 허용되지 않은 캠페인 상태 전이 (예: DRAFT → ENDED). */
    INVALID_STATUS_TRANSITION(
            "CAMPAIGN_019",
            "허용되지 않은 캠페인 상태 전이입니다.",
            HttpStatus.CONFLICT),

    /** DRAFT 외 상태에서 일반 필드 수정 시도. */
    NOT_MODIFIABLE(
            "CAMPAIGN_020",
            "DRAFT 상태의 캠페인만 수정할 수 있습니다.",
            HttpStatus.CONFLICT),

    /** 캠페인 조회 결과 없음 (소유권 불일치 포함 — IDOR 방지 위해 동일 코드 사용). */
    CAMPAIGN_NOT_FOUND(
            "CAMPAIGN_021",
            "존재하지 않는 캠페인입니다.",
            HttpStatus.NOT_FOUND),

    // ==================== 발송 상태 전이 (CAMPAIGN_022) ====================

    /** 허용되지 않은 CampaignExecution 상태 전이 (예: SUCCESS → FAIL). */
    INVALID_EXECUTION_TRANSITION(
            "CAMPAIGN_022",
            "허용되지 않은 발송 상태 전이입니다.",
            HttpStatus.CONFLICT),

    // ==================== 외부 BC 자원 참조 검증 (CAMPAIGN_023 ~ CAMPAIGN_024) ====================

    /** 캠페인 생성 시 지정한 프로젝트가 존재하지 않음. */
    CAMPAIGN_PROJECT_NOT_FOUND(
            "CAMPAIGN_023",
            "프로젝트를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND),

    /** 캠페인 생성 시 지정한 멤버가 존재하지 않음. */
    CAMPAIGN_MEMBER_NOT_FOUND(
            "CAMPAIGN_024",
            "멤버를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND),

    // ==================== 세그먼트 상태 검증 (CAMPAIGN_025 ~ CAMPAIGN_026) ====================

    /** 캠페인 ACTIVE 전이 시 세그먼트가 READY 상태가 아님 (422). */
    SEGMENT_NOT_READY(
            "CAMPAIGN_025",
            "세그먼트가 발송 가능한 상태가 아닙니다.",
            HttpStatus.UNPROCESSABLE_ENTITY),

    /** 캠페인 ACTIVE 전이 시 지정된 segmentId가 존재하지 않음 (404). */
    SEGMENT_NOT_FOUND(
            "CAMPAIGN_026",
            "세그먼트를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND),
    ;

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
