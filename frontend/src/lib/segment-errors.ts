import { ApiError } from "@/api/types";

/**
 * Sprint 5 — Segment 도메인 에러 코드 → UI 메시지 매핑.
 *
 * 백엔드 enum (직접 검증 완료):
 *  c:/ssafy/S14P31B101/backend/butterfly-segment/src/main/java/com/b101/butterfly/segment/exception/SegmentErrorCode.java
 *
 * 9개 코드:
 *  - SEGMENT_400_NAME_LENGTH         (400) "세그먼트 이름은 1자 이상 30자 이하여야 합니다." → field: name
 *  - SEGMENT_400_NAME_DUPLICATED     (400) "동일 프로젝트 내에 같은 이름의 세그먼트가 이미 존재합니다." → field: name
 *  - SEGMENT_400_DESCRIPTION_LENGTH  (400) "세그먼트 설명은 100자 이하여야 합니다." → field: description
 *  - SEGMENT_400_EMPTY_CONDITIONS    (400) "조건은 1개 이상이어야 합니다." → field: conditionTree
 *  - SEGMENT_400_INVALID_CONDITION_TREE (400) "조건 트리 스키마가 올바르지 않습니다." → field: conditionTree
 *  - SEGMENT_404_NOT_FOUND           (404) "세그먼트를 찾을 수 없습니다."
 *  - SEGMENT_409_LINKED_ACTIVE_CAMPAIGN (409) "진행 중인 캠페인에 연결되어 수정 또는 삭제할 수 없습니다."
 *  - SEGMENT_409_BUILD_IN_PROGRESS   (409) "세그먼트 빌드가 진행 중입니다."
 *  - SEGMENT_501_EVENT_NOT_READY     (501) "EVENT 조건은 event 모듈 합류 전까지 미리보기에 사용할 수 없습니다."
 */

export interface SegmentErrorMapping {
  field?: string;
  message: string;
  /** UI 측에서 토스트 vs 인라인 vs 페이지 헤더 등 분기 힌트. */
  display?: "field" | "toast" | "banner";
}

const FIELDS: Record<string, string> = {
  SEGMENT_400_NAME_LENGTH: "name",
  SEGMENT_400_NAME_DUPLICATED: "name",
  SEGMENT_400_DESCRIPTION_LENGTH: "description",
  SEGMENT_400_EMPTY_CONDITIONS: "conditionTree",
  SEGMENT_400_INVALID_CONDITION_TREE: "conditionTree",
};

const MESSAGES: Record<string, string> = {
  SEGMENT_400_NAME_LENGTH: "세그먼트 이름은 1자 이상 30자 이하여야 합니다",
  SEGMENT_400_NAME_DUPLICATED:
    "동일 프로젝트 내에 같은 이름의 세그먼트가 이미 존재합니다",
  SEGMENT_400_DESCRIPTION_LENGTH: "세그먼트 설명은 100자 이하여야 합니다",
  SEGMENT_400_EMPTY_CONDITIONS: "조건은 1개 이상이어야 합니다",
  SEGMENT_400_INVALID_CONDITION_TREE: "조건 트리 스키마가 올바르지 않습니다",
  SEGMENT_404_NOT_FOUND: "세그먼트를 찾을 수 없습니다",
  SEGMENT_409_LINKED_ACTIVE_CAMPAIGN:
    "진행 중인 캠페인에 연결되어 수정 또는 삭제할 수 없습니다",
  SEGMENT_409_BUILD_IN_PROGRESS: "세그먼트 빌드가 진행 중입니다",
  SEGMENT_501_EVENT_NOT_READY:
    "EVENT 조건은 event 모듈 합류 전까지 미리보기에 사용할 수 없습니다",
};

const DISPLAY: Record<string, "field" | "toast" | "banner"> = {
  SEGMENT_400_NAME_LENGTH: "field",
  SEGMENT_400_NAME_DUPLICATED: "field",
  SEGMENT_400_DESCRIPTION_LENGTH: "field",
  SEGMENT_400_EMPTY_CONDITIONS: "field",
  SEGMENT_400_INVALID_CONDITION_TREE: "field",
  SEGMENT_404_NOT_FOUND: "banner",
  SEGMENT_409_LINKED_ACTIVE_CAMPAIGN: "banner",
  SEGMENT_409_BUILD_IN_PROGRESS: "toast",
  SEGMENT_501_EVENT_NOT_READY: "banner",
};

/**
 * 캐시 invalidate 가 필요한 코드.
 * - 409 BUILD_IN_PROGRESS: 다른 사용자/프로세스가 빌드 트리거 → detail refetch 권장
 * - 409 LINKED_ACTIVE_CAMPAIGN: 캠페인 상태가 변경됨 → list / detail refetch 권장
 */
export const SEGMENT_REFRESH_CODES = new Set<string>([
  "SEGMENT_409_BUILD_IN_PROGRESS",
  "SEGMENT_409_LINKED_ACTIVE_CAMPAIGN",
]);

export function mapSegmentError(err: unknown): SegmentErrorMapping {
  if (err instanceof ApiError) {
    const code = err.code ?? "";
    const message = MESSAGES[code] ?? err.message ?? "요청 처리 중 오류가 발생했습니다";
    return {
      field: FIELDS[code],
      message,
      display: DISPLAY[code] ?? "toast",
    };
  }
  const message =
    err instanceof Error ? err.message : "일시적인 오류가 발생했습니다";
  return { message, display: "toast" };
}

export function isKnownSegmentCode(err: unknown): boolean {
  return (
    err instanceof ApiError &&
    typeof err.code === "string" &&
    err.code in MESSAGES
  );
}
