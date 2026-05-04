import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import * as campaignApi from "@/api/campaign";
import type {
  CampaignDetail,
  CampaignListResponse,
  CreateCampaignRequest,
  DeleteCampaignResponse,
  ListCampaignsParams,
  UpdateCampaignRequest,
} from "@/api/campaign";

/**
 * Sprint 6 — Campaign React Query 훅.
 *
 * 캐시 키:
 *  - ["campaigns", projectId, "list", params]
 *  - ["campaigns", projectId, "detail", id]
 *
 * 상태 전이 mutation (start/pause/delete) onSuccess 에서 list/detail 모두 invalidate.
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
  return useMutation<CampaignDetail, unknown, CreateCampaignRequest>({
    mutationFn: (req) => campaignApi.createCampaign(req),
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
  return useMutation<CampaignDetail, unknown, UpdateCampaignVariables>({
    mutationFn: ({ campaignId, req }) =>
      campaignApi.updateCampaign(campaignId, req),
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
  return useMutation<DeleteCampaignResponse, unknown, number>({
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
  return useMutation<CampaignDetail, unknown, number>({
    mutationFn: (campaignId) =>
      campaignApi.startCampaign(projectId, campaignId),
    onSuccess: (_data, campaignId) => {
      qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: campaignKeys.detail(projectId, campaignId),
      });
      // mock 시뮬: 1.5초 후 SUCCESS 전이 → 한 번 더 invalidate
      window.setTimeout(() => {
        qc.invalidateQueries({
          queryKey: campaignKeys.detail(projectId, campaignId),
        });
        qc.invalidateQueries({ queryKey: campaignKeys.lists(projectId) });
      }, 1700);
    },
  });
}

export function usePauseCampaignMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<CampaignDetail, unknown, number>({
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
