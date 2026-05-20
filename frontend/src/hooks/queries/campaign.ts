import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import * as campaignApi from "@/api/campaign";
import type {
  CampaignCreateResponse,
  CampaignDetail,
  CampaignListResponse,
  CreateCampaignRequest,
  ListCampaignsParams,
  StatusTransitionResponse,
  UpdateCampaignRequest,
} from "@/api/campaign";

/**
 * Sprint 6 — Campaign React Query 훅.
 *
 * S14P31B101-233 머지 후 전 7 endpoint 실 API. 시작/일시정지는
 * `PATCH /campaigns/{id}/status` 로 통합됨.
 */

export const campaignKeys = {
  all: (projectId: number) => ["campaigns", projectId] as const,
  list: (
    projectId: number,
    params: Omit<ListCampaignsParams, "projectId">,
  ) => ["campaigns", projectId, "list", params] as const,
  lists: (projectId: number) => ["campaigns", projectId, "list"] as const,
  detail: (projectId: number, id: number) =>
    ["campaigns", projectId, "detail", id] as const,
};

export type CampaignListQueryParams = Omit<ListCampaignsParams, "projectId">;

// ---------- Queries ----------

export function useCampaignListQuery(
  projectId: number | null,
  params: CampaignListQueryParams,
  enabled: boolean = true,
) {
  return useQuery<CampaignListResponse>({
    queryKey: campaignKeys.list(projectId ?? -1, params),
    queryFn: () =>
      campaignApi.listCampaigns({ projectId: projectId as number, ...params }),
    enabled: enabled && projectId != null,
  });
}

export function useCampaignDetailQuery(
  projectId: number | null,
  campaignId: number | null,
  enabled: boolean = true,
  options?: { refetchInterval?: number | false },
) {
  return useQuery<CampaignDetail>({
    queryKey: campaignKeys.detail(projectId ?? -1, campaignId ?? -1),
    queryFn: () =>
      campaignApi.getCampaign(projectId as number, campaignId as number),
    enabled: enabled && projectId != null && campaignId != null,
    refetchInterval: options?.refetchInterval,
  });
}

// ---------- Mutations ----------

export function useCreateCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<CampaignCreateResponse, unknown, CreateCampaignRequest>({
    mutationFn: (req) => campaignApi.createCampaign(projectId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
    },
  });
}

export interface UpdateCampaignVariables {
  campaignId: number;
  req: UpdateCampaignRequest;
}

export function useUpdateCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<CampaignCreateResponse, unknown, UpdateCampaignVariables>({
    mutationFn: ({ campaignId, req }) =>
      campaignApi.updateCampaign(projectId, campaignId, req),
    onSuccess: (_data, { campaignId }) => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: campaignKeys.detail(projectId, campaignId),
      });
    },
  });
}

export function useDeleteCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<void, unknown, number>({
    mutationFn: (campaignId) =>
      campaignApi.deleteCampaign(projectId, campaignId),
    onSuccess: (_data, campaignId) => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: campaignKeys.detail(projectId, campaignId),
      });
    },
  });
}

export function useStartCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<StatusTransitionResponse, unknown, number>({
    mutationFn: (campaignId) =>
      campaignApi.startCampaign(projectId, campaignId),
    onSuccess: (_data, campaignId) => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: campaignKeys.detail(projectId, campaignId),
      });
    },
  });
}

export function usePauseCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<StatusTransitionResponse, unknown, number>({
    mutationFn: (campaignId) =>
      campaignApi.pauseCampaign(projectId, campaignId),
    onSuccess: (_data, campaignId) => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: campaignKeys.detail(projectId, campaignId),
      });
    },
  });
}

export function useResumeCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<StatusTransitionResponse, unknown, number>({
    mutationFn: (campaignId) =>
      campaignApi.resumeCampaign(projectId, campaignId),
    onSuccess: (_data, campaignId) => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: campaignKeys.detail(projectId, campaignId),
      });
    },
  });
}

export function useCancelCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<StatusTransitionResponse, unknown, number>({
    mutationFn: (campaignId) =>
      campaignApi.cancelCampaign(projectId, campaignId),
    onSuccess: (_data, campaignId) => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: campaignKeys.detail(projectId, campaignId),
      });
    },
  });
}

export function useDuplicateCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<CampaignCreateResponse, unknown, number>({
    mutationFn: (campaignId) =>
      campaignApi.duplicateCampaign(projectId, campaignId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
    },
  });
}
