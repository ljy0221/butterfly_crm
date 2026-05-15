import { api } from "./client";

/**
 * 5/22 시연용 — 사용자 프로필 조회.
 * 백엔드: GET /api/v1/projects/{projectId}/users/{userId} (UserProfileController)
 */

export type Platform = "IOS" | "ANDROID" | "WEB";

export interface UserDeviceSummary {
  platform: Platform;
  appVersion: string;
  osVersion: string;
  tokenValid: boolean;
  lastActiveAt: string | null;
}

export interface UserActivitySummary {
  totalEvents: number;
  lastSeenAt: string | null;
  purchaseCount: number;
}

export interface UserRecentEvent {
  eventId: number;
  eventCategory: string;
  occurredAt: string;
  /** 백엔드 @JsonRawValue로 직렬화된 이벤트 페이로드. 객체일 수도 있고 null일 수도 있음. */
  eventData: Record<string, unknown> | null;
}

export interface UserProfile {
  userId: number;
  externalId: string;
  pushOptIn: boolean;
  signedUpAt: string | null;
  device: UserDeviceSummary | null;
  summary: UserActivitySummary;
  recentEvents: UserRecentEvent[];
}

export async function getUserProfile(
  projectId: number,
  userId: number,
): Promise<UserProfile> {
  const res = await api.get<UserProfile>(
    `/api/v1/projects/${projectId}/users/${userId}`,
  );
  return res.data;
}
