import { ApiError } from "@/api/types";

/**
 * Sprint 7 — Event 도메인 에러 코드 → UI 메시지 매핑.
 *
 * 백엔드 enum:
 *  c:/ssafy/S14P31B101/backend/butterfly-event/.../EventErrorCode.java
 *  - CATEGORY_NOT_FOUND (404)
 *  - INTERNAL_ERROR     (500)
 *
 * mock store 가 던지는 추가 코드:
 *  - CATEGORY_DUPLICATE  (이미 존재)
 *  - PROPERTY_DUPLICATE  (속성 키 중복)
 *  - BUILTIN_NOT_DELETABLE
 */

export interface EventErrorMapping {
  field?: string;
  message: string;
  display: "field" | "toast" | "banner";
}

const MAPPING: Record<string, EventErrorMapping> = {
  CATEGORY_NOT_FOUND: {
    message: "등록되지 않은 이벤트 카테고리입니다",
    display: "banner",
  },
  CATEGORY_DUPLICATE: {
    field: "categoryName",
    message: "이미 등록된 이벤트 카테고리입니다",
    display: "field",
  },
  PROPERTY_DUPLICATE: {
    message: "속성 키가 중복됩니다",
    display: "toast",
  },
  BUILTIN_NOT_DELETABLE: {
    message: "기본 이벤트 카테고리는 삭제할 수 없습니다",
    display: "toast",
  },
  INTERNAL_ERROR: {
    message: "서버 내부 오류가 발생했습니다",
    display: "banner",
  },
};

export function mapEventError(err: unknown): EventErrorMapping {
  if (err instanceof ApiError) {
    const code = err.code ?? "";
    const hit = MAPPING[code];
    if (hit) return hit;
    return {
      message: err.message ?? "요청 처리 중 오류가 발생했습니다",
      display: "toast",
    };
  }
  if (err && typeof err === "object" && "code" in err) {
    const code = String((err as { code?: unknown }).code ?? "");
    const hit = MAPPING[code];
    if (hit) return hit;
  }
  const message =
    err instanceof Error ? err.message : "일시적인 오류가 발생했습니다";
  return { message, display: "toast" };
}
