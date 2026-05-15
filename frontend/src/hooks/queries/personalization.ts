import { useQuery } from "@tanstack/react-query";
import {
  listRecommendedVariables,
  type RecommendedVariableResponse,
} from "@/api/personalization";

/**
 * 프로젝트(+옵션 세그먼트)별 추천 변수 React Query 훅.
 *
 * 캐시 키: ["personalization-vars", projectId, segmentId ?? null]
 * projectId 가 null/음수면 비활성. 빈 응답 (`variables: []`) 은 호출 측에서
 * fallback (예: 하드코딩 기본 변수) 으로 대체.
 */
export const personalizationKeys = {
  all: ["personalization-vars"] as const,
  byProject: (projectId: number, segmentId?: number | null) =>
    ["personalization-vars", projectId, segmentId ?? null] as const,
};

export function useRecommendedVariablesQuery(
  projectId: number | null,
  segmentId?: number | null,
) {
  return useQuery<RecommendedVariableResponse>({
    queryKey: personalizationKeys.byProject(projectId ?? -1, segmentId),
    queryFn: () =>
      listRecommendedVariables(projectId as number, segmentId),
    enabled: projectId != null && projectId > 0,
    staleTime: 60_000,
  });
}
