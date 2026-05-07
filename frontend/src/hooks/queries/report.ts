import { useQuery } from "@tanstack/react-query";
import * as reportApi from "@/api/report";
import type {
  Channel,
  SegmentChannelMatrixResponse,
  SegmentChannelMatrixSortBy,
} from "@/api/report";

/**
 * S14P31B101-92 — Reporting React Query 훅.
 *
 * 캐시 키 prefix:
 *  - ["report", "segment-channel-matrix", projectId, from, to, channels, segmentIds, sortBy, ascending]
 *
 * staleTime: 5분 — 백엔드 집계가 daily 기반(_stats_daily 테이블 우선 조회)이라
 * 잦은 refetch 불필요. dashboard hourly 카드(STALE 60초)와는 다른 정책.
 */

const STALE_TIME = 5 * 60_000;

export const reportKeys = {
  segmentChannelMatrix: (
    projectId: number,
    from: string,
    to: string,
    channels: Channel[] | undefined,
    segmentIds: number[] | undefined,
    sortBy: SegmentChannelMatrixSortBy | undefined,
    ascending: boolean,
  ) =>
    [
      "report",
      "segment-channel-matrix",
      projectId,
      from,
      to,
      [...(channels ?? [])].sort(),
      [...(segmentIds ?? [])].sort((a, b) => a - b),
      sortBy ?? null,
      ascending,
    ] as const,
};

export interface UseSegmentChannelMatrixArgs {
  projectId: number | null;
  from: string;
  to: string;
  channels?: Channel[];
  segmentIds?: number[];
  sortBy?: SegmentChannelMatrixSortBy;
  ascending?: boolean;
  /** OWNER 권한 미달이면 호출 자체를 막아 403 토스트를 피한다. */
  enabled?: boolean;
}

export function useSegmentChannelMatrixQuery({
  projectId,
  from,
  to,
  channels,
  segmentIds,
  sortBy,
  ascending = false,
  enabled = true,
}: UseSegmentChannelMatrixArgs) {
  return useQuery<SegmentChannelMatrixResponse>({
    queryKey: reportKeys.segmentChannelMatrix(
      projectId ?? -1,
      from,
      to,
      channels,
      segmentIds,
      sortBy,
      ascending,
    ),
    queryFn: () =>
      reportApi.getSegmentChannelMatrix(projectId as number, {
        from,
        to,
        channels,
        segmentIds,
        sortBy,
        ascending,
      }),
    enabled: enabled && projectId != null && !!from && !!to,
    staleTime: STALE_TIME,
  });
}
