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
  /** 해당 key 를 보유한 사용자 수. */
  userCount: number;
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
