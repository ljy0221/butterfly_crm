import { useQuery } from "@tanstack/react-query";
import * as analyticsApi from "@/api/analytics";
import type {
  CampaignMetricItem,
  ChannelMetricsResponse,
  DashboardResponse,
  SpringPage,
} from "@/api/analytics";

/**
 * Sprint 3 — Analytics 도메인 React Query 훅.
 *
 * 캐시 키 prefix:
 *  - ["analytics", "dashboard", projectId, from, to]
 *  - ["analytics", "channels",  projectId, from, to]
 *  - ["analytics", "campaigns", projectId, from, to, page, size, channel?, segmentId?]
 *
 * staleTime 60초 — 메트릭이 실시간 집계가 아니라 배치 갱신이므로 잦은 refetch 불필요.
 */

const STALE_TIME = 60_000;

export const analyticsKeys = {
  dashboard: (projectId: number, from: string, to: string) =>
    ["analytics", "dashboard", projectId, from, to] as const,
  channels: (projectId: number, from: string, to: string) =>
    ["analytics", "channels", projectId, from, to] as const,
  campaigns: (
    projectId: number,
    from: string,
    to: string,
    page: number,
    size: number,
    channel?: string,
    segmentId?: number,
  ) =>
    [
      "analytics",
      "campaigns",
      projectId,
      from,
      to,
      page,
      size,
      channel ?? null,
      segmentId ?? null,
    ] as const,
};

interface PeriodArgs {
  projectId: number | null;
  from: string;
  to: string;
}

export function useDashboardQuery({ projectId, from, to }: PeriodArgs) {
  return useQuery<DashboardResponse>({
    queryKey: analyticsKeys.dashboard(projectId ?? -1, from, to),
    queryFn: () =>
      analyticsApi.getDashboard(projectId as number, { from, to }),
    enabled: projectId != null && !!from && !!to,
    staleTime: STALE_TIME,
  });
}

export function useChannelMetricsQuery({ projectId, from, to }: PeriodArgs) {
  return useQuery<ChannelMetricsResponse>({
    queryKey: analyticsKeys.channels(projectId ?? -1, from, to),
    queryFn: () =>
      analyticsApi.getChannelMetrics(projectId as number, { from, to }),
    enabled: projectId != null && !!from && !!to,
    staleTime: STALE_TIME,
  });
}

interface RecentCampaignsArgs extends PeriodArgs {
  size?: number;
}

/**
 * 대시보드 "최근 캠페인 5건" 카드 전용. /analytics/campaigns?page=0&size=N.
 * 백엔드 정렬 기본값은 명시 미합의 — Backend Liaison follow-up 항목.
 */
export function useRecentCampaignsQuery({
  projectId,
  from,
  to,
  size = 5,
}: RecentCampaignsArgs) {
  return useQuery<SpringPage<CampaignMetricItem>>({
    queryKey: analyticsKeys.campaigns(projectId ?? -1, from, to, 0, size),
    queryFn: () =>
      analyticsApi.getCampaignMetrics(projectId as number, {
        from,
        to,
        page: 0,
        size,
      }),
    enabled: projectId != null && !!from && !!to,
    staleTime: STALE_TIME,
  });
}
