import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";
import * as segmentApi from "@/api/segment";
import type {
  CreateSegmentResponse,
  FavoritesResponse,
  LinkedCampaignsResponse,
  ListSegmentsParams,
  PreviewResponse,
  RebuildResponse,
  CancelBuildResponse,
  SegmentConditionMetadata,
  SegmentDetail,
  SegmentListResponse,
  SegmentSummary,
  SegmentUserListResponse,
} from "@/api/segment";
import type { GroupNode } from "@/schemas/segment";

/**
 * Sprint 5 — Segment React Query 훅.
 *
 * 캐시 키 prefix:
 *  - ["segments", projectId, "list", params]
 *  - ["segments", projectId, "detail", id]
 *  - ["segments", projectId, "favorites"]
 *  - ["segments", projectId, "linkedCampaigns", id]
 *  - ["segments", projectId, "users", id, page, size]
 *
 * 즐겨찾기 토글은 onMutate 에서 list / favorites 캐시를 직접 업데이트(낙관적),
 * onError 에서 롤백, onSettled 에서 invalidate.
 *
 * Rebuild 는 호출 직후 즉시 BUILDING 으로 detail 캐시 patch 하고,
 * 5초 뒤 invalidate 하여 mock 의 READY 전이가 반영되도록 한다.
 */

export const segmentKeys = {
  all: (projectId: number) => ["segments", projectId] as const,
  conditionMetadata: (projectId: number) =>
    ["segments", projectId, "conditionMetadata"] as const,
  list: (projectId: number, params: ListSegmentsParams) =>
    ["segments", projectId, "list", params] as const,
  lists: (projectId: number) =>
    ["segments", projectId, "list"] as const,
  detail: (projectId: number, id: number) =>
    ["segments", projectId, "detail", id] as const,
  favorites: (projectId: number) =>
    ["segments", projectId, "favorites"] as const,
  linkedCampaigns: (projectId: number, id: number) =>
    ["segments", projectId, "linkedCampaigns", id] as const,
  users: (projectId: number, id: number, page: number, size: number) =>
    ["segments", projectId, "users", id, page, size] as const,
};

// ---------- Queries ----------

export function useSegmentListQuery(
  projectId: number | null,
  params: ListSegmentsParams,
  enabled: boolean = true,
) {
  return useQuery<SegmentListResponse>({
    queryKey: segmentKeys.list(projectId ?? -1, params),
    queryFn: () => segmentApi.listSegments(projectId as number, params),
    enabled: enabled && projectId != null,
  });
}

export function useSegmentConditionMetadataQuery(
  projectId: number | null,
  enabled: boolean = true,
) {
  return useQuery<SegmentConditionMetadata>({
    queryKey: segmentKeys.conditionMetadata(projectId ?? -1),
    queryFn: () => segmentApi.getConditionMetadata(projectId as number),
    enabled: enabled && projectId != null,
    staleTime: 5 * 60 * 1000,
  });
}

export function useSegmentDetailQuery(
  projectId: number | null,
  segmentId: number | null,
  enabled: boolean = true,
  options?: {
    /** 데이터를 받아 폴링 주기(ms)나 false 를 반환. PENDING/BUILDING 동안 단축 폴링용. */
    refetchInterval?: (data: SegmentDetail | undefined) => number | false;
  },
) {
  return useQuery<SegmentDetail>({
    queryKey: segmentKeys.detail(projectId ?? -1, segmentId ?? -1),
    queryFn: () =>
      segmentApi.getSegment(projectId as number, segmentId as number),
    enabled: enabled && projectId != null && segmentId != null,
    refetchInterval: options?.refetchInterval
      ? (query) => options.refetchInterval!(query.state.data)
      : undefined,
  });
}

export function useFavoritesQuery(
  projectId: number | null,
  enabled: boolean = true,
) {
  return useQuery<FavoritesResponse>({
    queryKey: segmentKeys.favorites(projectId ?? -1),
    queryFn: () => segmentApi.listFavorites(projectId as number),
    enabled: enabled && projectId != null,
  });
}

export function useLinkedCampaignsQuery(
  projectId: number | null,
  segmentId: number | null,
  enabled: boolean = true,
) {
  return useQuery<LinkedCampaignsResponse>({
    queryKey: segmentKeys.linkedCampaigns(projectId ?? -1, segmentId ?? -1),
    queryFn: () =>
      segmentApi.getLinkedCampaigns(projectId as number, segmentId as number),
    enabled: enabled && projectId != null && segmentId != null,
  });
}

export function useSegmentUsersQuery(
  projectId: number | null,
  segmentId: number | null,
  page: number = 0,
  size: number = 20,
  enabled: boolean = true,
) {
  return useQuery<SegmentUserListResponse>({
    queryKey: segmentKeys.users(projectId ?? -1, segmentId ?? -1, page, size),
    queryFn: () =>
      segmentApi.listSegmentUsers(
        projectId as number,
        segmentId as number,
        page,
        size,
      ),
    enabled: enabled && projectId != null && segmentId != null,
  });
}

// ---------- Mutations ----------

export interface CreateSegmentVariables {
  name: string;
  description: string;
  conditionTree: GroupNode;
}

export function useCreateSegmentMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<CreateSegmentResponse, unknown, CreateSegmentVariables>({
    mutationFn: (vars) => segmentApi.createSegment(projectId, vars),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: segmentKeys.lists(projectId) });
      qc.invalidateQueries({ queryKey: segmentKeys.favorites(projectId) });
    },
  });
}

export interface UpdateSegmentVariables {
  segmentId: number;
  name: string;
  description: string;
  conditionTree: GroupNode;
}

export function useUpdateSegmentMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<SegmentDetail, unknown, UpdateSegmentVariables>({
    mutationFn: ({ segmentId, ...rest }) =>
      segmentApi.updateSegment(projectId, segmentId, rest),
    onSuccess: (_data, { segmentId }) => {
      qc.invalidateQueries({ queryKey: segmentKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: segmentKeys.detail(projectId, segmentId),
      });
      qc.invalidateQueries({ queryKey: segmentKeys.favorites(projectId) });
    },
  });
}

export function useDeleteSegmentMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<void, unknown, number>({
    mutationFn: (segmentId) => segmentApi.deleteSegment(projectId, segmentId),
    onSuccess: (_data, segmentId) => {
      qc.invalidateQueries({ queryKey: segmentKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: segmentKeys.detail(projectId, segmentId),
      });
      qc.invalidateQueries({ queryKey: segmentKeys.favorites(projectId) });
    },
  });
}

export function usePreviewSegmentMutation(projectId: number) {
  return useMutation<PreviewResponse, unknown, GroupNode>({
    mutationFn: (tree) => segmentApi.previewSegment(projectId, tree),
  });
}

export function useRebuildSegmentMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<RebuildResponse, unknown, number>({
    mutationFn: (segmentId) => segmentApi.rebuildSegment(projectId, segmentId),
    onSuccess: (data, segmentId) => {
      // 백엔드가 API 시점에 segment.buildStatus 를 PENDING 으로 마크하므로 응답 즉시 반영.
      // 이후 useSegmentDetailQuery 의 refetchInterval(PENDING/BUILDING 일 때 3s)가
      // BUILDING→READY 전이를 자동 감지하므로 별도 setTimeout 불필요.
      qc.setQueryData<SegmentDetail | undefined>(
        segmentKeys.detail(projectId, segmentId),
        (prev) => (prev ? { ...prev, buildStatus: data.buildStatus } : prev),
      );
      qc.invalidateQueries({ queryKey: segmentKeys.lists(projectId) });
    },
  });
}

export function useCancelSegmentBuildMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<CancelBuildResponse, unknown, number>({
    mutationFn: (segmentId) => segmentApi.cancelSegmentBuild(projectId, segmentId),
    onSuccess: (data, segmentId) => {
      qc.setQueryData<SegmentDetail | undefined>(
        segmentKeys.detail(projectId, segmentId),
        (prev) =>
          prev
            ? {
                ...prev,
                buildStatus: data.buildStatus,
                buildStartedAt: null,
                currentMemberCount: prev.estimatedCount,
              }
            : prev,
      );
      qc.invalidateQueries({ queryKey: segmentKeys.lists(projectId) });
      qc.invalidateQueries({
        queryKey: segmentKeys.detail(projectId, segmentId),
      });
    },
  });
}

// ---------- 즐겨찾기 토글 (낙관적 업데이트) ----------

export interface ToggleFavoriteVariables {
  segmentId: number;
  /** 토글 후 목표 상태. true=추가, false=제거. */
  next: boolean;
}

interface ToggleFavoriteContext {
  prevList: Array<{
    queryKey: ReadonlyArray<unknown>;
    data: SegmentListResponse | undefined;
  }>;
  prevFavorites: FavoritesResponse | undefined;
}

/**
 * 즐겨찾기 토글 — 낙관적 업데이트.
 *
 * onMutate:
 *   - 모든 list 캐시 스냅샷 후 favorited 토글
 *   - favorites 캐시 직접 수정 (favorites 배열 add/remove)
 * onError:
 *   - 스냅샷으로 롤백
 * onSettled:
 *   - list / favorites invalidate (서버 정합성 확보)
 */
export function useToggleFavoriteMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<
    void,
    unknown,
    ToggleFavoriteVariables,
    ToggleFavoriteContext
  >({
    mutationFn: ({ segmentId, next }) =>
      next
        ? segmentApi.addFavorite(projectId, segmentId)
        : segmentApi.removeFavorite(projectId, segmentId),
    onMutate: async ({ segmentId, next }) => {
      await qc.cancelQueries({ queryKey: segmentKeys.lists(projectId) });
      await qc.cancelQueries({ queryKey: segmentKeys.favorites(projectId) });

      // 1) list 캐시 직접 수정
      const prevList = patchListCaches(qc, projectId, segmentId, next);

      // 2) favorites 캐시 직접 수정
      const favKey = segmentKeys.favorites(projectId);
      const prevFavorites = qc.getQueryData<FavoritesResponse>(favKey);
      if (prevFavorites) {
        const updated = patchFavoritesCache(
          prevFavorites,
          segmentId,
          next,
          findSegmentName(qc, projectId, segmentId) ?? "",
        );
        qc.setQueryData<FavoritesResponse>(favKey, updated);
      }

      return { prevList, prevFavorites };
    },
    onError: (_err, _vars, ctx) => {
      if (!ctx) return;
      for (const { queryKey, data } of ctx.prevList) {
        qc.setQueryData(queryKey, data);
      }
      qc.setQueryData(segmentKeys.favorites(projectId), ctx.prevFavorites);
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: segmentKeys.lists(projectId) });
      qc.invalidateQueries({ queryKey: segmentKeys.favorites(projectId) });
    },
  });
}

// ---------- helpers ----------

function patchListCaches(
  qc: QueryClient,
  projectId: number,
  segmentId: number,
  next: boolean,
): Array<{
  queryKey: ReadonlyArray<unknown>;
  data: SegmentListResponse | undefined;
}> {
  const entries = qc.getQueriesData<SegmentListResponse>({
    queryKey: segmentKeys.lists(projectId),
  });
  const snapshots: Array<{
    queryKey: ReadonlyArray<unknown>;
    data: SegmentListResponse | undefined;
  }> = [];
  for (const [queryKey, data] of entries) {
    snapshots.push({ queryKey, data });
    if (!data) continue;
    const updated: SegmentListResponse = {
      ...data,
      content: data.content.map((s) =>
        s.segmentId === segmentId ? { ...s, favorited: next } : s,
      ),
    };
    qc.setQueryData<SegmentListResponse>(queryKey, updated);
  }
  return snapshots;
}

function patchFavoritesCache(
  prev: FavoritesResponse,
  segmentId: number,
  next: boolean,
  fallbackName: string,
): FavoritesResponse {
  if (next) {
    if (prev.favorites.some((f) => f.segmentId === segmentId)) return prev;
    // 목록에서 이름·estimatedCount 가져올 수 없으면 fallback 사용
    const insert = {
      segmentId,
      name: fallbackName || `세그먼트 #${segmentId}`,
      estimatedCount: null,
      favoritedAt: new Date().toISOString(),
    };
    return { ...prev, favorites: [insert, ...prev.favorites] };
  }
  return {
    ...prev,
    favorites: prev.favorites.filter((f) => f.segmentId !== segmentId),
  };
}

function findSegmentName(
  qc: QueryClient,
  projectId: number,
  segmentId: number,
): string | null {
  // 1) list 캐시에서 우선 검색
  const lists = qc.getQueriesData<SegmentListResponse>({
    queryKey: segmentKeys.lists(projectId),
  });
  for (const [, data] of lists) {
    const hit = data?.content.find(
      (s: SegmentSummary) => s.segmentId === segmentId,
    );
    if (hit) return hit.name;
  }
  // 2) detail 캐시
  const detail = qc.getQueryData<SegmentDetail>(
    segmentKeys.detail(projectId, segmentId),
  );
  return detail?.name ?? null;
}
