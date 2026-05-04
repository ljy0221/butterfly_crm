import { ApiError } from "@/api/types";

/**
 * Sprint 6 — Campaign 도메인 에러 코드 → UI 메시지/필드힌트 매핑.
 *
 * 백엔드 enum (직접 검증 완료):
 *  c:/ssafy/S14P31B101/backend/butterfly-campaign/.../campaign/exception/code/CampaignErrorCode.java
 *
 * 14개 도메인 코드 + 3개 실행 코드 + 2개 상태 전이 + 1개 동시성 = 16+ 코드.
 *
 * jumpStep: 위저드에서 해당 에러 발생 시 점프할 step 번호 (1~6).
 */

export interface CampaignErrorMapping {
  field?: string;
  message: string;
  display: "field" | "toast" | "banner";
  /** 위저드 점프 대상 step (없으면 detail/list 페이지 컨텍스트). */
  jumpStep?: 1 | 2 | 3 | 4 | 5 | 6;
}

const MAPPING: Record<string, CampaignErrorMapping> = {
  // 공통 검증 — Step 1
  CAMPAIGN_001: {
    message: "프로젝트 정보가 올바르지 않습니다",
    display: "banner",
  },
  CAMPAIGN_002: {
    field: "messageTemplateId",
    message: "메시지 템플릿을 선택해주세요",
    display: "field",
    jumpStep: 5,
  },
  CAMPAIGN_003: {
    message: "로그인 정보가 올바르지 않습니다",
    display: "banner",
  },
  CAMPAIGN_004: {
    field: "campaignName",
    message: "캠페인 이름은 필수입니다",
    display: "field",
    jumpStep: 1,
  },
  CAMPAIGN_005: {
    field: "campaignDescription",
    message: "캠페인 설명은 필수입니다",
    display: "field",
    jumpStep: 1,
  },
  // 세그먼트 — Step 3
  CAMPAIGN_006: {
    field: "segmentId",
    message: "세그먼트를 선택해주세요",
    display: "field",
    jumpStep: 3,
  },
  // 예약 — Step 4
  CAMPAIGN_007: {
    field: "scheduledAt",
    message: "예약 발송 시각은 필수입니다",
    display: "field",
    jumpStep: 4,
  },
  CAMPAIGN_008: {
    field: "scheduledAt",
    message: "발송 예정 시각은 현재보다 과거일 수 없습니다",
    display: "field",
    jumpStep: 4,
  },
  CAMPAIGN_009: {
    field: "repeatType",
    message: "반복 유형을 선택해주세요",
    display: "field",
    jumpStep: 4,
  },
  CAMPAIGN_010: {
    field: "recurringCount",
    message: "목표 반복 발송 횟수는 1 이상이어야 합니다",
    display: "field",
    jumpStep: 4,
  },
  CAMPAIGN_011: {
    field: "repeatTime",
    message: "반복 발송 시각은 필수입니다",
    display: "field",
    jumpStep: 4,
  },
  CAMPAIGN_012: {
    field: "repeatDayOfWeek",
    message: "주간 반복 시 요일은 필수입니다",
    display: "field",
    jumpStep: 4,
  },
  CAMPAIGN_013: {
    field: "repeatDayOfWeek",
    message: "요일 값은 MON~SUN 만 사용 가능합니다",
    display: "field",
    jumpStep: 4,
  },
  CAMPAIGN_014: {
    field: "repeatDayOfMonth",
    message: "월간 반복 일자는 1~31 사이여야 합니다",
    display: "field",
    jumpStep: 4,
  },
  // 실행 이력
  CAMPAIGN_015: {
    message: "캠페인 정보가 올바르지 않습니다",
    display: "banner",
  },
  CAMPAIGN_016: {
    message: "실행 회차는 1 이상이어야 합니다",
    display: "toast",
  },
  CAMPAIGN_017: {
    message: "발송 대상자 수는 0 이상이어야 합니다",
    display: "toast",
  },
  // 상태 전이
  CAMPAIGN_NOT_FOUND: {
    message: "캠페인을 찾을 수 없습니다",
    display: "banner",
  },
  CAMPAIGN_ALREADY_ACTIVE: {
    message: "이미 시작된 캠페인이거나 상태 전이가 불가합니다",
    display: "toast",
  },
  CAMPAIGN_NOT_ACTIVE: {
    message: "ACTIVE 상태의 캠페인만 일시정지할 수 있습니다",
    display: "toast",
  },
  // 동시성
  OPTIMISTIC_LOCK_FAILURE: {
    message: "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해주세요",
    display: "banner",
  },
};

/** 캐시 invalidate 가 필요한 코드 — 다른 사용자/시간이 상태를 변경한 케이스. */
export const CAMPAIGN_REFRESH_CODES = new Set<string>([
  "CAMPAIGN_ALREADY_ACTIVE",
  "CAMPAIGN_NOT_ACTIVE",
  "OPTIMISTIC_LOCK_FAILURE",
  "CAMPAIGN_NOT_FOUND",
]);

export function mapCampaignError(err: unknown): CampaignErrorMapping {
  if (err instanceof ApiError) {
    const code = err.code ?? "";
    const hit = MAPPING[code];
    if (hit) return hit;
    return {
      message: err.message ?? "요청 처리 중 오류가 발생했습니다",
      display: "toast",
    };
  }
  // mock store 에서 throw 한 Error (.code 부착)
  if (err && typeof err === "object" && "code" in err) {
    const code = String((err as { code?: unknown }).code ?? "");
    const hit = MAPPING[code];
    if (hit) return hit;
  }
  const message =
    err instanceof Error ? err.message : "일시적인 오류가 발생했습니다";
  return { message, display: "toast" };
}

export function isKnownCampaignCode(err: unknown): boolean {
  if (err instanceof ApiError && typeof err.code === "string") {
    return err.code in MAPPING;
  }
  if (err && typeof err === "object" && "code" in err) {
    const code = String((err as { code?: unknown }).code ?? "");
    return code in MAPPING;
  }
  return false;
}
