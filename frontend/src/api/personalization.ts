import { api } from "./client";

/**
 * 메시지 템플릿 편집의 "추천 변수" 패널 데이터.
 *
 * 백엔드: com.b101.butterfly.event.controller.PersonalizationVariableController
 *   GET /api/v1/projects/{projectId}/personalization-variables?segmentId=
 *
 * 산출 기준: 프로젝트(+옵션 세그먼트) user_tags 의 distinct tag_key 빈도 상위 N.
 */

export interface RecommendedVariableItem {
  /** 변수명 (tag_key) — 클릭 시 본문에 `{{key}}` 로 삽입됨. */
  key: string;
  /** 기존 호환 필드. 신규 응답에서는 availableCount 와 동일 의미. */
  userCount: number;
  /** 변수 출처. */
  source?: "USER_TAG" | "COUPON" | "CAMPAIGN" | "SYSTEM" | "EVENT_CONTEXT";
  /** 선택 세그먼트 기준 사용 가능 상태. */
  status?: "READY" | "PARTIAL" | "MISSING" | "SYSTEM_PROVIDED";
  /** 값을 보유한 대상자 수. */
  availableCount?: number;
  /** 값을 보유하지 않은 대상자 수. */
  missingCount?: number;
  /** 본문에 삽입할 토큰. */
  insertToken?: string;
}

export interface RecommendedVariableResponse {
  variables: RecommendedVariableItem[];
}

export async function listRecommendedVariables(
  projectId: number,
  segmentId?: number | null,
): Promise<RecommendedVariableResponse> {
  const res = await api.get<RecommendedVariableResponse>(
    `/api/v1/projects/${projectId}/personalization-variables`,
    {
      params: segmentId != null ? { segmentId } : undefined,
    },
  );
  return res.data;
}
