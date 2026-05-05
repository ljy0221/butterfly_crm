import { ApiError } from "@/api/types";

/**
 * Sprint 7 — Audit(분석) 도메인 에러 코드 → UI 메시지 매핑.
 *
 * 백엔드 enum:
 *  c:/ssafy/S14P31B101/backend/butterfly-reporting/.../AnalyticsErrorCode.java
 *  - ANALYTICS_001 (캠페인 통계 NOT_FOUND, 404)
 *  - ANALYTICS_002 (잘못된 날짜 범위, 400)
 *  - ANALYTICS_003 (조회 기간 너무 김, 400)
 *  - ANALYTICS_004 (감사 로그 NOT_FOUND, 404)
 */

export interface AuditErrorMapping {
  field?: string;
  message: string;
  display: "field" | "toast" | "banner";
}

const MAPPING: Record<string, AuditErrorMapping> = {
  ANALYTICS_001: {
    message: "캠페인 통계를 찾을 수 없습니다",
    display: "banner",
  },
  ANALYTICS_002: {
    field: "endDate",
    message: "시작일은 종료일보다 이전이어야 합니다",
    display: "field",
  },
  ANALYTICS_003: {
    field: "endDate",
    message: "조회 기간이 너무 깁니다 (최대 90일)",
    display: "field",
  },
  ANALYTICS_004: {
    message: "감사 로그를 찾을 수 없습니다",
    display: "banner",
  },
};

export function mapAuditError(err: unknown): AuditErrorMapping {
  if (err instanceof ApiError) {
    const code = err.code ?? "";
    const hit = MAPPING[code];
    if (hit) return hit;
    if (err.status === 403) {
      return {
        message: "감사 로그 열람은 OWNER 이상 권한이 필요합니다",
        display: "banner",
      };
    }
    return {
      message: err.message ?? "요청 처리 중 오류가 발생했습니다",
      display: "toast",
    };
  }
  const message =
    err instanceof Error ? err.message : "일시적인 오류가 발생했습니다";
  return { message, display: "toast" };
}
