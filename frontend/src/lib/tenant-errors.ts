import { ApiError } from "@/api/types";

/**
 * Sprint 2 — Tenant 도메인 에러 코드 → UI 메시지 매핑.
 *
 * 백엔드 enum (직접 검증 완료):
 *  c:/ssafy/S14P31B101/backend/butterfly-tenant/src/main/java/com/b101/butterfly/tenant/exception/TenantErrorCode.java
 *
 * Tenant 코드:
 *  - PROJ_002 (404) PROJECT_NOT_FOUND          — "존재하지 않는 프로젝트입니다"
 *  - PROJ_003 (409) PROJECT_HAS_ACTIVE_CAMPAIGN — "진행 중인 캠페인이 있어 삭제할 수 없습니다"
 *  - PROJ_004 (403) PROJECT_ACCESS_DENIED      — "접근 권한이 없습니다"
 *  - PROJ_005 (409) API_KEY_ALREADY_EXISTS     — "이미 발급된 키가 존재합니다. 재발급을 이용해주세요"
 *  - PROJ_006 (404) MEMBER_NOT_IN_PROJECT      — "프로젝트에 소속되지 않은 멤버입니다"
 *  - PROJ_007 (409) CANNOT_DELETE_OWNER        — "Owner는 삭제할 수 없습니다"
 *  - PROJ_008 (409) OWNER_TRANSFER_REQUIRED    — "Owner 권한 이전은 별도 절차가 필요합니다"
 *
 * field?: 폼 필드 인라인 에러로 표시할지 여부(이 도메인은 대부분 토스트 레벨이라 미사용).
 */

export interface TenantErrorMapping {
  field?: string;
  message: string;
}

const DEFAULT_MESSAGES: Record<string, string> = {
  PROJ_002: "존재하지 않는 프로젝트입니다",
  PROJ_003: "진행 중인 캠페인이 있어 삭제할 수 없습니다",
  PROJ_004: "이 작업을 수행할 권한이 없습니다",
  PROJ_005: "이미 발급된 API 키가 존재합니다. 재발급을 이용해주세요",
  PROJ_006: "이 멤버는 더 이상 프로젝트에 소속되지 않습니다",
  PROJ_007: "OWNER는 삭제할 수 없습니다",
  PROJ_008: "OWNER 권한 이전은 별도 절차가 필요합니다",
  // Common
  COMMON_001: "요청이 올바르지 않습니다",
  COMMON_002: "인증이 필요합니다",
  COMMON_003: "접근 권한이 없습니다",
  COMMON_004: "리소스를 찾을 수 없습니다",
  COMMON_005: "리소스 상태가 충돌합니다",
  COMMON_999: "서버 내부 오류가 발생했습니다",
};

/**
 * 멤버 목록 invalidate 가 필요한 코드.
 *  - PROJ_006: 멤버가 사라졌으니 목록 갱신 후 사용자 안내
 *  - PROJ_007: 단순 거절(이미 OWNER), 데이터 정합성은 유지되므로 invalidate 불필요
 */
export const TENANT_REFRESH_CODES = new Set<string>(["PROJ_006"]);

export function mapTenantError(err: unknown): TenantErrorMapping {
  if (err instanceof ApiError) {
    const code = err.code ?? "";
    const message =
      DEFAULT_MESSAGES[code] ??
      err.message ??
      "요청 처리 중 오류가 발생했습니다";
    return { message };
  }
  const message =
    err instanceof Error ? err.message : "일시적인 오류가 발생했습니다";
  return { message };
}

/**
 * 사용자에게 보여줄 코드인지 판단(개발용 디버그가 아닌 명시 코드만 true).
 */
export function isKnownTenantCode(err: unknown): boolean {
  return (
    err instanceof ApiError &&
    typeof err.code === "string" &&
    err.code in DEFAULT_MESSAGES
  );
}
