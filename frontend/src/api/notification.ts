import { api } from "./client";
import type {
  CredentialChannel,
  NotificationChannel,
  NotificationStatus,
} from "@/schemas/notification";
import type { PageResponse } from "./types";

/**
 * Sprint 7 — Notification API.
 *
 * 백엔드 (전부 실 API, 8개):
 *  - PUT    /api/projects/{pid}/credentials/{channel}
 *  - GET    /api/projects/{pid}/credentials/{channel}
 *  - DELETE /api/projects/{pid}/credentials/{channel}
 *  - POST   /api/projects/{pid}/fcm/send
 *  - DELETE /api/projects/{pid}/fcm/credentials/cache
 *  - GET    /api/projects/{pid}/campaigns/{cid}/logs
 *  - GET    /api/projects/{pid}/logs/{logId}
 *  - GET    /api/projects/{pid}/users/{userId}/logs
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap.
 */

// ---------- 백엔드 record DTO ----------

export interface NotificationLogResponse {
  logId: number;
  campaignId: number;
  userId: number;
  deviceId: number | null;
  channel: NotificationChannel | null;
  status: NotificationStatus;
  /** ISO LocalDateTime */
  sentAt: string | null;
  deliveredAt: string | null;
  clickedAt: string | null;
}

export interface NotificationLogDetailResponse {
  logId: number;
  projectId: number;
  campaignId: number;
  userId: number;
  deviceId: number | null;
  channel: NotificationChannel | null;
  recipient: string | null;
  status: NotificationStatus;
  errorCode: string | null;
  errorMessage: string | null;
  sentAt: string | null;
  deliveredAt: string | null;
  clickedAt: string | null;
  createdAt: string;
}

export interface FcmTokenResult {
  token: string;
  ok: boolean;
  errorCode?: string | null;
  errorMessage?: string | null;
  messageId?: string | null;
}

export interface FcmSendResponse {
  successCount: number;
  failureCount: number;
  results: FcmTokenResult[];
}

export interface ChannelCredentialRequest {
  credentialData: string;
}

export interface FcmSendRequest {
  credentialJson: string;
  tokens: string[];
  title?: string;
  body?: string;
  imageUrl?: string;
}

export interface DeliveredCallbackRequest {
  /** ISO LocalDateTime */
  deliveredAt: string;
}

export interface ClickedCallbackRequest {
  clickedAt: string;
}

export interface CallbackResponse {
  ok: boolean;
}

// ---------- 1. credentials ----------

export async function saveCredential(
  projectId: number,
  channel: CredentialChannel,
  req: ChannelCredentialRequest,
): Promise<void> {
  await api.put<null>(
    `/api/projects/${projectId}/credentials/${channel}`,
    req,
  );
}

export async function getCredential(
  projectId: number,
  channel: CredentialChannel,
): Promise<string> {
  const res = await api.get<string>(
    `/api/projects/${projectId}/credentials/${channel}`,
  );
  return res.data;
}

export async function deleteCredential(
  projectId: number,
  channel: CredentialChannel,
): Promise<void> {
  await api.delete<null>(
    `/api/projects/${projectId}/credentials/${channel}`,
  );
}

// ---------- 2. FCM ----------

export async function sendFcm(
  projectId: number,
  req: FcmSendRequest,
): Promise<FcmSendResponse> {
  const res = await api.post<FcmSendResponse>(
    `/api/projects/${projectId}/fcm/send`,
    req,
  );
  return res.data;
}

export async function invalidateFcmCache(projectId: number): Promise<void> {
  await api.delete<null>(`/api/projects/${projectId}/fcm/credentials/cache`);
}

// ---------- 3. logs ----------

export interface CampaignLogsParams {
  channel?: NotificationChannel;
  status?: NotificationStatus;
  page?: number;
  size?: number;
}

export async function listCampaignLogs(
  projectId: number,
  campaignId: number,
  params: CampaignLogsParams = {},
): Promise<PageResponse<NotificationLogResponse>> {
  const res = await api.get<PageResponse<NotificationLogResponse>>(
    `/api/projects/${projectId}/campaigns/${campaignId}/logs`,
    {
      params: {
        channel: params.channel,
        status: params.status,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    },
  );
  return res.data;
}

export interface UserLogsParams {
  page?: number;
  size?: number;
}

export async function listUserLogs(
  projectId: number,
  userId: number,
  params: UserLogsParams = {},
): Promise<PageResponse<NotificationLogResponse>> {
  const res = await api.get<PageResponse<NotificationLogResponse>>(
    `/api/projects/${projectId}/users/${userId}/logs`,
    {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    },
  );
  return res.data;
}

export async function getLogDetail(
  projectId: number,
  logId: number,
): Promise<NotificationLogDetailResponse> {
  const res = await api.get<NotificationLogDetailResponse>(
    `/api/projects/${projectId}/logs/${logId}`,
  );
  return res.data;
}

// ---------- 4. SDK 콜백 (운영 콘솔 비사용 — 노출만) ----------

export async function markDelivered(
  logId: number,
  apiKey: string,
  req: DeliveredCallbackRequest,
): Promise<CallbackResponse> {
  const res = await api.post<CallbackResponse>(
    `/sdk/v1/notifications/${logId}/delivered`,
    req,
    { headers: { "X-API-Key": apiKey } },
  );
  return res.data;
}

export async function markClicked(
  logId: number,
  apiKey: string,
  req: ClickedCallbackRequest,
): Promise<CallbackResponse> {
  const res = await api.post<CallbackResponse>(
    `/sdk/v1/notifications/${logId}/clicked`,
    req,
    { headers: { "X-API-Key": apiKey } },
  );
  return res.data;
}
