import { api } from "./client";
import { ifMock } from "./_mock";
import {
  getLinkedCampaignsMock,
  listSegmentUsersMock,
  rebuildSegmentMock,
  type BuildStatus,
  type LinkedCampaignsResponseMock,
  type PageResponseMock,
  type RebuildResponseMock,
  type SegmentUserMockItem,
} from "./_mock/segment";
import {
  fromBackendTree,
  toBackendTree,
  type BackendGroupNode,
  type GroupNode,
} from "@/schemas/segment";

/**
 * Sprint 5 — Segment API.
 *
 * 백엔드: com.b101.butterfly.segment.controller.SegmentController
 *
 * 실 API (8개):
 *  - POST   /api/v1/projects/{pid}/segments
 *  - GET    /api/v1/projects/{pid}/segments
 *  - GET    /api/v1/projects/{pid}/segments/{sid}
 *  - PUT    /api/v1/projects/{pid}/segments/{sid}
 *  - DELETE /api/v1/projects/{pid}/segments/{sid}
 *  - POST   /api/v1/projects/{pid}/segments/preview
 *  - GET    /api/v1/projects/{pid}/segments/favorites
 *  - POST   /api/v1/projects/{pid}/segments/{sid}/favorite
 *  - DELETE /api/v1/projects/{pid}/segments/{sid}/favorite
 *
 * Mock-fallback (3개, ifMock 래핑):
 *  - GET   /segments/{sid}/users        (segment_users)
 *  - POST  /segments/{sid}/rebuild      (segment_rebuild)
 *  - GET   /segments/{sid}/campaigns    (segment_linked_campaigns)
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap.
 */

// ---------- Re-exports ----------

export type { BuildStatus };
export type {
  GroupNode,
  ConditionNode,
  EventNode,
  UserNode,
  TagNode,
  DeviceNode,
  Period,
  Operator,
  ConditionLogic,
  ConditionValue,
  EventFilter,
} from "@/schemas/segment";

// ---------- 백엔드 record 매핑 DTO ----------

export interface CreatorSummary {
  memberId: number;
  name: string;
}

export interface LinkedCampaignSummary {
  campaignId: number;
  name: string;
  campaignStatus: string;
}

export interface SegmentSummary {
  segmentId: number;
  name: string;
  description: string;
  estimatedCount: number | null;
  buildStatus: BuildStatus;
  createdBy: CreatorSummary;
  createdAt: string;
  updatedAt: string;
  linkedCampaignCount: number;
  favorited: boolean;
}

/**
 * GET /segments 응답.
 * 백엔드 PageResponse<SegmentSummaryResponse> — `page` 필드 (Spring Data PageImpl 의 number 가 아님).
 */
export interface SegmentListResponse {
  content: SegmentSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SegmentDetail {
  segmentId: number;
  name: string;
  description: string;
  conditionTree: GroupNode;
  schemaVersion: number;
  estimatedCount: number | null;
  estimatedAt: string | null;
  lastBuiltAt: string | null;
  buildStatus: BuildStatus;
  /** S14P31B101-275 — BUILDING/PENDING 중에는 실시간 적재된 segment_members COUNT, 그 외엔 estimatedCount. */
  currentMemberCount: number | null;
  /** 가장 최근 빌드 시작 시각 (BUILDING 중 progress 계산용). */
  buildStartedAt: string | null;
  createdBy: CreatorSummary;
  createdAt: string;
  updatedAt: string;
  linkedCampaigns: LinkedCampaignSummary[];
  favorited: boolean;
}

export interface CreateSegmentResponse {
  segmentId: number;
  name: string;
  description: string;
  buildStatus: BuildStatus;
  estimatedCount: number | null;
  estimatedAt: string | null;
  lastBuiltAt: string | null;
  schemaVersion: number;
  createdBy: number;
  createdAt: string;
}

export interface PreviewResponse {
  estimatedCount: number;
  /** "HLL" | "EXACT" */
  method: string;
  estimatedAt: string;
}

export interface FavoriteItem {
  segmentId: number;
  name: string;
  estimatedCount: number | null;
  favoritedAt: string;
}

export interface RecentlyUsedItem {
  segmentId: number;
  name: string;
  estimatedCount: number | null;
  lastUsedAt: string;
}

export interface FavoritesResponse {
  favorites: FavoriteItem[];
  recentlyUsed: RecentlyUsedItem[];
}

export interface RebuildResponse {
  segmentId: number;
  buildStatus: BuildStatus;
  queuedAt: string;
}

export interface LinkedCampaignsResponse {
  active: LinkedCampaignSummary[];
  paused: LinkedCampaignSummary[];
  draft: LinkedCampaignSummary[];
  ended: LinkedCampaignSummary[];
  totalCount: number;
}

export interface SegmentUserItem {
  userId: number;
  externalId: string;
  email: string;
  lastActiveAt: string;
  matchedAt: string;
}

export interface SegmentUserListResponse {
  content: SegmentUserItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------- 요청 본문 ----------

export interface CreateSegmentRequest {
  name: string;
  description: string;
  conditionTree: BackendGroupNode;
}

/** PUT 본문은 POST 와 동일 record. */
export type UpdateSegmentRequest = CreateSegmentRequest;

export interface PreviewRequest {
  conditionTree: BackendGroupNode;
}

// ---------- 목록 파라미터 ----------

export interface ListSegmentsParams {
  page?: number;
  size?: number;
  /** Spring Data Pageable sort — "createdAt,desc" 같은 형식. */
  sort?: string;
}

// ---------- 어댑터 ----------

interface BackendSegmentDetail extends Omit<SegmentDetail, "conditionTree"> {
  conditionTree: BackendGroupNode;
}

function detailFromBackend(raw: BackendSegmentDetail): SegmentDetail {
  return {
    ...raw,
    conditionTree: fromBackendTree(raw.conditionTree),
  };
}

// ---------- 엔드포인트 (실 API 8개) ----------

/** 1. POST /api/v1/projects/{pid}/segments — EDITOR↑. */
export async function createSegment(
  projectId: number,
  req: { name: string; description: string; conditionTree: GroupNode },
): Promise<CreateSegmentResponse> {
  const body: CreateSegmentRequest = {
    name: req.name,
    description: req.description,
    conditionTree: toBackendTree(req.conditionTree),
  };
  const res = await api.post<CreateSegmentResponse>(
    `/api/v1/projects/${projectId}/segments`,
    body,
  );
  return res.data;
}

/** 2. GET /api/v1/projects/{pid}/segments — VIEWER↑. */
export async function listSegments(
  projectId: number,
  params: ListSegmentsParams = {},
): Promise<SegmentListResponse> {
  const res = await api.get<SegmentListResponse>(
    `/api/v1/projects/${projectId}/segments`,
    {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 20,
        sort: params.sort ?? "createdAt,desc",
      },
    },
  );
  return res.data;
}

/** 3. GET /api/v1/projects/{pid}/segments/{sid} — VIEWER↑. */
export async function getSegment(
  projectId: number,
  segmentId: number,
): Promise<SegmentDetail> {
  const res = await api.get<BackendSegmentDetail>(
    `/api/v1/projects/${projectId}/segments/${segmentId}`,
  );
  return detailFromBackend(res.data);
}

/** 4. PUT /api/v1/projects/{pid}/segments/{sid} — EDITOR↑. */
export async function updateSegment(
  projectId: number,
  segmentId: number,
  req: { name: string; description: string; conditionTree: GroupNode },
): Promise<SegmentDetail> {
  const body: UpdateSegmentRequest = {
    name: req.name,
    description: req.description,
    conditionTree: toBackendTree(req.conditionTree),
  };
  const res = await api.put<BackendSegmentDetail>(
    `/api/v1/projects/${projectId}/segments/${segmentId}`,
    body,
  );
  return detailFromBackend(res.data);
}

/** 5. DELETE /api/v1/projects/{pid}/segments/{sid} — EDITOR↑. */
export async function deleteSegment(
  projectId: number,
  segmentId: number,
): Promise<void> {
  await api.delete<null>(
    `/api/v1/projects/${projectId}/segments/${segmentId}`,
  );
}

/** 6. POST /api/v1/projects/{pid}/segments/preview — EDITOR↑. */
export async function previewSegment(
  projectId: number,
  conditionTree: GroupNode,
): Promise<PreviewResponse> {
  const body: PreviewRequest = {
    conditionTree: toBackendTree(conditionTree),
  };
  const res = await api.post<PreviewResponse>(
    `/api/v1/projects/${projectId}/segments/preview`,
    body,
  );
  return res.data;
}

/** 7. GET /api/v1/projects/{pid}/segments/favorites — VIEWER↑. */
export async function listFavorites(
  projectId: number,
): Promise<FavoritesResponse> {
  const res = await api.get<FavoritesResponse>(
    `/api/v1/projects/${projectId}/segments/favorites`,
  );
  return res.data;
}

/** 8. POST /api/v1/projects/{pid}/segments/{sid}/favorite — VIEWER↑. */
export async function addFavorite(
  projectId: number,
  segmentId: number,
): Promise<void> {
  await api.post<null>(
    `/api/v1/projects/${projectId}/segments/${segmentId}/favorite`,
  );
}

/** 9. DELETE /api/v1/projects/{pid}/segments/{sid}/favorite — VIEWER↑. */
export async function removeFavorite(
  projectId: number,
  segmentId: number,
): Promise<void> {
  await api.delete<null>(
    `/api/v1/projects/${projectId}/segments/${segmentId}/favorite`,
  );
}

// ---------- 엔드포인트 (mock-fallback 3개) ----------

/** 10. GET /api/v1/projects/{pid}/segments/{sid}/users — VIEWER↑. mock-fallback. */
export async function listSegmentUsers(
  projectId: number,
  segmentId: number,
  page: number = 0,
  size: number = 20,
): Promise<SegmentUserListResponse> {
  return ifMock<SegmentUserListResponse>(
    "segment_users",
    async (): Promise<SegmentUserListResponse> => {
      const r: PageResponseMock<SegmentUserMockItem> = await listSegmentUsersMock(
        segmentId,
        page,
        size,
      );
      return r;
    },
    async () => {
      const res = await api.get<SegmentUserListResponse>(
        `/api/v1/projects/${projectId}/segments/${segmentId}/users`,
        { params: { page, size } },
      );
      return res.data;
    },
  );
}

/** 11. POST /api/v1/projects/{pid}/segments/{sid}/rebuild — EDITOR↑. mock-fallback. */
export async function rebuildSegment(
  projectId: number,
  segmentId: number,
): Promise<RebuildResponse> {
  return ifMock<RebuildResponse>(
    "segment_rebuild",
    async (): Promise<RebuildResponse> => {
      const r: RebuildResponseMock = await rebuildSegmentMock(segmentId);
      return r;
    },
    async () => {
      const res = await api.post<RebuildResponse>(
        `/api/v1/projects/${projectId}/segments/${segmentId}/rebuild`,
      );
      return res.data;
    },
  );
}

/** 12. GET /api/v1/projects/{pid}/segments/{sid}/campaigns — VIEWER↑. mock-fallback. */
export async function getLinkedCampaigns(
  projectId: number,
  segmentId: number,
): Promise<LinkedCampaignsResponse> {
  return ifMock<LinkedCampaignsResponse>(
    "segment_linked_campaigns",
    async (): Promise<LinkedCampaignsResponse> => {
      const r: LinkedCampaignsResponseMock = await getLinkedCampaignsMock(segmentId);
      return r;
    },
    async () => {
      const res = await api.get<LinkedCampaignsResponse>(
        `/api/v1/projects/${projectId}/segments/${segmentId}/campaigns`,
      );
      return res.data;
    },
  );
}
