import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as notificationApi from "@/api/notification";
import type {
  CampaignLogsParams,
  ChannelCredentialRequest,
  FcmSendRequest,
  FcmSendResponse,
  NotificationLogDetailResponse,
  NotificationLogResponse,
  UserLogsParams,
} from "@/api/notification";
import type { CredentialChannel } from "@/schemas/notification";
import type { PageResponse } from "@/api/types";

/**
 * Sprint 7 — Notification React Query 훅.
 *
 * 캐시 키:
 *  - ["notification", projectId, "campaign-logs", campaignId, params]
 *  - ["notification", projectId, "user-logs", userId, params]
 *  - ["notification", projectId, "log-detail", logId]
 *  - ["notification", projectId, "credential", channel]
 */

export const notificationKeys = {
  all: (projectId: number) => ["notification", projectId] as const,
  campaignLogs: (
    projectId: number,
    campaignId: number,
    params: CampaignLogsParams,
  ) =>
    [
      "notification",
      projectId,
      "campaign-logs",
      campaignId,
      params,
    ] as const,
  userLogs: (projectId: number, userId: number, params: UserLogsParams) =>
    ["notification", projectId, "user-logs", userId, params] as const,
  logDetail: (projectId: number, logId: number) =>
    ["notification", projectId, "log-detail", logId] as const,
  credential: (projectId: number, channel: CredentialChannel) =>
    ["notification", projectId, "credential", channel] as const,
};

// ---------- Queries ----------

export function useCampaignLogsQuery(
  projectId: number | null,
  campaignId: number | null,
  params: CampaignLogsParams,
  enabled: boolean = true,
) {
  return useQuery<PageResponse<NotificationLogResponse>>({
    queryKey: notificationKeys.campaignLogs(
      projectId ?? -1,
      campaignId ?? -1,
      params,
    ),
    queryFn: () =>
      notificationApi.listCampaignLogs(
        projectId as number,
        campaignId as number,
        params,
      ),
    enabled: enabled && projectId != null && campaignId != null,
  });
}

export function useUserLogsQuery(
  projectId: number | null,
  userId: number | null,
  params: UserLogsParams,
  enabled: boolean = true,
) {
  return useQuery<PageResponse<NotificationLogResponse>>({
    queryKey: notificationKeys.userLogs(
      projectId ?? -1,
      userId ?? -1,
      params,
    ),
    queryFn: () =>
      notificationApi.listUserLogs(
        projectId as number,
        userId as number,
        params,
      ),
    enabled: enabled && projectId != null && userId != null,
  });
}

export function useLogDetailQuery(
  projectId: number | null,
  logId: number | null,
  enabled: boolean = true,
) {
  return useQuery<NotificationLogDetailResponse>({
    queryKey: notificationKeys.logDetail(projectId ?? -1, logId ?? -1),
    queryFn: () =>
      notificationApi.getLogDetail(projectId as number, logId as number),
    enabled: enabled && projectId != null && logId != null,
  });
}

export function useCredentialQuery(
  projectId: number | null,
  channel: CredentialChannel | null,
  enabled: boolean = true,
) {
  return useQuery<string>({
    queryKey: notificationKeys.credential(
      projectId ?? -1,
      (channel ?? "PUSH") as CredentialChannel,
    ),
    queryFn: () =>
      notificationApi.getCredential(
        projectId as number,
        channel as CredentialChannel,
      ),
    enabled: enabled && projectId != null && channel != null,
    // 404 = 미등록 상태 — 재시도 불필요. retry 기본값(3)이면 재시도마다 404가 발생해
    // 불필요한 네트워크 요청이 늘고 상태 표시도 지연됨.
    retry: false,
  });
}

// ---------- Mutations ----------

export interface SaveCredentialVariables {
  channel: CredentialChannel;
  req: ChannelCredentialRequest;
}

export function useSaveCredentialMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<void, unknown, SaveCredentialVariables>({
    mutationFn: ({ channel, req }) =>
      notificationApi.saveCredential(projectId, channel, req),
    onSuccess: (_data, { channel }) => {
      qc.invalidateQueries({
        queryKey: notificationKeys.credential(projectId, channel),
      });
    },
  });
}

export function useDeleteCredentialMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<void, unknown, CredentialChannel>({
    mutationFn: (channel) =>
      notificationApi.deleteCredential(projectId, channel),
    onSuccess: (_data, channel) => {
      qc.invalidateQueries({
        queryKey: notificationKeys.credential(projectId, channel),
      });
    },
  });
}

export function useSendFcmMutation(projectId: number) {
  return useMutation<FcmSendResponse, unknown, FcmSendRequest>({
    mutationFn: (req) => notificationApi.sendFcm(projectId, req),
  });
}

export function useInvalidateFcmCacheMutation(projectId: number) {
  return useMutation<void, unknown, void>({
    mutationFn: () => notificationApi.invalidateFcmCache(projectId),
  });
}
