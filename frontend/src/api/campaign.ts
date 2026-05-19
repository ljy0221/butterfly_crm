import { api } from "./client";
import type {
  CampaignStatus,
  CampaignType,
  Channel,
  SendMode,
} from "@/schemas/campaign";

/**
 * Sprint 6 — Campaign API.
 *
 * 백엔드: com.b101.butterfly.campaign.lifecycle.presentation.CampaignController
 * 베이스 경로: `/api/v1/projects/{projectId}/campaigns`
 *
 * S14P31B101-233 머지 후 전 7 endpoint 실 API. 응답은 ApiResponse 래퍼.
 *
 * ⚠️ schedule(예약·반복) 세부 정보는 백엔드 CampaignDetailResponse 에 미노출 (D-1 보류).
 * 위저드에서 받은 schedule 상태는 현재 백엔드에 전달되지 않으며, 상세 조회 시에도 반환되지 않는다.
 */

// ---------- 요청 ----------

export interface CreateCampaignRequest {
  messageTemplateId: number;
  segmentId: number | null;
  campaignName: string;
  campaignDescription: string;
}

export type UpdateCampaignRequest = CreateCampaignRequest;

export interface ListCampaignsParams {
  projectId: number;
  status?: CampaignStatus;
  channel?: Channel;
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

// ---------- 응답 ----------

export interface CampaignCreateResponse {
  campaignId: number;
}

export interface CampaignListItem {
  campaignId: number;
  campaignName: string;
  channel: Channel;
  campaignStatus: CampaignStatus;
  createdAt: string;
}

/** 백엔드 `PageResponse<T>` 그대로. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CampaignListResponse {
  campaigns: CampaignListItem[];
  pagination: {
    currentPage: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
  };
}

export interface CampaignDetail {
  campaignId: number;
  projectId: number;
  messageTemplateId: number;
  segmentId: number | null;
  memberId: number;
  campaignName: string;
  campaignDescription: string;
  campaignType: CampaignType;
  sendMode: SendMode;
  channel: Channel;
  campaignStatus: CampaignStatus;
  createdAt: string;
  updatedAt: string;
  segmentBuildStatus: string | null;
}

export interface StatusTransitionResponse {
  campaignId: number;
  campaignStatus: CampaignStatus;
}

// ---------- 어댑터 ----------

function toListResponse(p: PageResponse<CampaignListItem>): CampaignListResponse {
  return {
    campaigns: p.content,
    pagination: {
      currentPage: p.page,
      totalPages: p.totalPages,
      totalElements: p.totalElements,
      pageSize: p.size,
    },
  };
}

// ---------- 엔드포인트 ----------

/** GET /api/v1/projects/{projectId}/campaigns — VIEWER↑ */
export async function listCampaigns(
  params: ListCampaignsParams,
): Promise<CampaignListResponse> {
  const res = await api.get<PageResponse<CampaignListItem>>(
    `/api/v1/projects/${params.projectId}/campaigns`,
    {
      params: {
        status: params.status,
        channel: params.channel,
        q: params.q,
        page: params.page ?? 0,
        size: params.size ?? 20,
        sort: params.sort,
      },
    },
  );
  return toListResponse(res.data);
}

/** GET /api/v1/projects/{projectId}/campaigns/{campaignId} — VIEWER↑ */
export async function getCampaign(
  projectId: number,
  campaignId: number,
): Promise<CampaignDetail> {
  const res = await api.get<CampaignDetail>(
    `/api/v1/projects/${projectId}/campaigns/${campaignId}`,
  );
  return res.data;
}

/**
 * POST /api/v1/projects/{projectId}/campaigns — EDITOR↑.
 * 백엔드는 `{ campaignId }` 만 반환하므로 상세는 호출 측에서 재조회한다.
 */
export async function createCampaign(
  projectId: number,
  req: CreateCampaignRequest,
): Promise<CampaignCreateResponse> {
  const res = await api.post<CampaignCreateResponse>(
    `/api/v1/projects/${projectId}/campaigns`,
    req,
  );
  return res.data;
}

/**
 * PATCH /api/v1/projects/{projectId}/campaigns/{campaignId} — EDITOR↑.
 * PUT-like PATCH — 모든 필드 항상 명시.
 */
export async function updateCampaign(
  projectId: number,
  campaignId: number,
  req: UpdateCampaignRequest,
): Promise<CampaignCreateResponse> {
  const res = await api.patch<CampaignCreateResponse>(
    `/api/v1/projects/${projectId}/campaigns/${campaignId}`,
    req,
  );
  return res.data;
}

/** DELETE /api/v1/projects/{projectId}/campaigns/{campaignId} — EDITOR↑ (soft delete). */
export async function deleteCampaign(
  projectId: number,
  campaignId: number,
): Promise<void> {
  await api.delete(`/api/v1/projects/${projectId}/campaigns/${campaignId}`);
}

/** PATCH /api/v1/projects/{projectId}/campaigns/{campaignId}/status — EDITOR↑. */
export async function transitionCampaignStatus(
  projectId: number,
  campaignId: number,
  status: CampaignStatus,
): Promise<StatusTransitionResponse> {
  const res = await api.patch<StatusTransitionResponse>(
    `/api/v1/projects/${projectId}/campaigns/${campaignId}/status`,
    { status },
  );
  return res.data;
}

/** `ACTIVE`로 상태 전이. */
export function startCampaign(
  projectId: number,
  campaignId: number,
): Promise<StatusTransitionResponse> {
  return transitionCampaignStatus(projectId, campaignId, "ACTIVE");
}

/** `PAUSED`로 상태 전이. */
export function pauseCampaign(
  projectId: number,
  campaignId: number,
): Promise<StatusTransitionResponse> {
  return transitionCampaignStatus(projectId, campaignId, "PAUSED");
}

/** `ACTIVE`로 재개 전이 (PAUSED → ACTIVE). */
export function resumeCampaign(
  projectId: number,
  campaignId: number,
): Promise<StatusTransitionResponse> {
  return transitionCampaignStatus(projectId, campaignId, "ACTIVE");
}

// ---------- 도메인 enum re-export (편의) ----------
export type {
  CampaignStatus,
  CampaignType,
  Channel,
  SendMode,
};
