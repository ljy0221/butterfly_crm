import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as appApi from "@/api/application";
import type {
  ApplicationCreateRequest,
  ApplicationListParams,
  ApplicationRejectRequest,
  ApplicationResponse,
  ApplicationStatus,
  SpringPage,
} from "@/api/application";

/**
 * Sprint 1 — Application(서비스 신청서) React Query 훅.
 *
 * 캐시 키:
 *  - ["applications", { status, page, size }]
 *  - ["application", applicationId]
 */

export const applicationKeys = {
  all: ["applications"] as const,
  list: (params: { status?: ApplicationStatus; page?: number; size?: number }) =>
    [...applicationKeys.all, params] as const,
  detail: (applicationId: number) =>
    ["application", applicationId] as const,
};

export function useApplicationsQuery(params: ApplicationListParams = {}) {
  return useQuery<SpringPage<ApplicationResponse>>({
    queryKey: applicationKeys.list({
      status: params.status,
      page: params.page ?? 0,
      size: params.size ?? 20,
    }),
    queryFn: () => appApi.listApplications(params),
  });
}

export function useApplicationQuery(
  applicationId: number | null,
  enabled: boolean = true,
) {
  return useQuery<ApplicationResponse>({
    queryKey: applicationKeys.detail(applicationId ?? -1),
    queryFn: () => appApi.getApplication(applicationId as number),
    enabled: enabled && applicationId !== null,
  });
}

export function useCreateApplicationMutation() {
  return useMutation<number, unknown, ApplicationCreateRequest>({
    mutationFn: (req) => appApi.createApplication(req),
  });
}

export function useApproveMutation() {
  const qc = useQueryClient();
  return useMutation<void, unknown, number>({
    mutationFn: (applicationId) => appApi.approveApplication(applicationId),
    onSuccess: (_data, applicationId) => {
      qc.invalidateQueries({ queryKey: applicationKeys.all });
      qc.invalidateQueries({ queryKey: applicationKeys.detail(applicationId) });
    },
  });
}

export function useRejectMutation() {
  const qc = useQueryClient();
  return useMutation<
    void,
    unknown,
    { applicationId: number; req: ApplicationRejectRequest }
  >({
    mutationFn: ({ applicationId, req }) =>
      appApi.rejectApplication(applicationId, req),
    onSuccess: (_data, { applicationId }) => {
      qc.invalidateQueries({ queryKey: applicationKeys.all });
      qc.invalidateQueries({ queryKey: applicationKeys.detail(applicationId) });
    },
  });
}
