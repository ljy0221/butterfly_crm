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
 * staleTime 60초 — 메트릭이 실시간 집계가 아니라 배치 갱신(hourly 5분 주기 +
 * daily 01:00 KST)이므로 60초 미만으로 줄여도 동일 응답이 반환된다. 발표 시연
 * 동선에서는 시드 데이터가 미리 깔린 상태에서 화면 이동만 발생하므로 60초 캐시가
 * 오히려 refetch 비용을 줄이고 UX 를 빠르게 한다. 캐시 신선도는 응답의
 * metricsComputedAt 으로 사용자가 확인할 수 있다.
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
 *
 * 백엔드가 `sort` 파라미터를 받지 않아 송신하지 않는다 — 정렬 순서는 백엔드 디폴트에 위임.
 */
export function useRecentCampaignsQuery({
  projectId,
  from,
  to,
  size = 5,
}: RecentCampaignsArgs) {
  return useQuery<SpringPage<CampaignMetricItem>>({
    queryKey: analyticsKeys.campaigns(
      projectId ?? -1,
      from,
      to,
      0,
      size,
    ),
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
