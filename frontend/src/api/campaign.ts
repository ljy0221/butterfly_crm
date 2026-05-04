import { api } from "./client";
import { ifMock } from "./_mock";
import {
  createCampaignMock,
  deleteCampaignMock,
  getCampaignMock,
  listCampaignsMock,
  pauseCampaignMock,
  startCampaignMock,
  updateCampaignMock,
  type CampaignDetailMock,
  type CampaignExecutionRecord,
  type CampaignListItemMock,
  type CampaignListMockParams,
  type CampaignListMockResponse,
  type CreateCampaignMockRequest,
  type ScheduleRecord,
  type UpdateCampaignMockRequest,
} from "./_mock/campaign";
import type {
  CampaignStatus,
  CampaignType,
  Channel,
  ExecutionStatus,
  RepeatType,
  SendMode,
} from "@/schemas/campaign";

/**
 * Sprint 6 — Campaign API.
 *
 * 백엔드: com.b101.butterfly.campaign.campaign (도메인만 완성, REST 미구현).
 * 7개 엔드포인트 모두 ifMock("campaign") 으로 감싸 mock-fallback. 백엔드 추가 구현 시
 * src/api/_mock/index.ts 의 campaign 플래그를 false 로 바꾸면 real 경로 활성화.
 */

// ---------- 공통 타입 (mock 도메인을 그대로 export) ----------

export type CampaignSchedule = ScheduleRecord;
export type {
  CampaignDetailMock as CampaignDetail,
  CampaignListItemMock as CampaignListItem,
  CampaignExecutionRecord as CampaignExecution,
  CampaignListMockResponse,
};

// ---------- 요청/응답 DTO (백엔드 명세 §3 기반) ----------

export interface CampaignListResponse {
  campaigns: CampaignListItemMock[];
  pagination: {
    currentPage: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
  };
}

export interface ListCampaignsParams {
  projectId: number;
  status?: CampaignStatus;
  channel?: Channel;
  q?: string;
  page?: number;
  size?: number;
  sort?: "createdAt,desc" | "createdAt,asc" | "name,asc";
}

export interface CreateCampaignRequest {
  projectId: number;
  campaignName: string;
  campaignDescription: string;
  campaignType: CampaignType;
  channel: Channel;
  segmentId: number | null;
  messageTemplateId: number;
  schedule: CampaignSchedule;
  isTempSave: boolean;
  /** mock-only — 표시용 메타. 백엔드 호출 시 무시됨. */
  meta?: {
    messageTemplateName?: string;
    segmentName?: string | null;
    createdBy?: string;
  };
}

export type UpdateCampaignRequest = CreateCampaignRequest;

export interface DeleteCampaignResponse {
  campaignId: number;
}

// 미구현 백엔드 호출용 — schedule 을 백엔드 record 형식으로 직렬화
function scheduleToBackend(s: CampaignSchedule): {
  sendMode: SendMode;
  scheduledAt?: string;
  repeat?: {
    repeatType: RepeatType;
    repeatTime: string;
    repeatDayOfWeek?: string;
    repeatDayOfMonth?: number;
    recurringCount: number;
    nextRunAt?: string;
  };
} {
  if (s.type === "immediate") return { sendMode: "IMMEDIATE" };
  if (s.type === "scheduled")
    return { sendMode: "SCHEDULED", scheduledAt: s.scheduledAt };
  return {
    sendMode: "RECURRING",
    repeat: {
      repeatType: s.repeatType,
      repeatTime: s.repeatTime,
      repeatDayOfWeek: s.repeatDayOfWeek,
      repeatDayOfMonth: s.repeatDayOfMonth,
      recurringCount: s.recurringCount,
      nextRunAt: s.nextRunAt,
    },
  };
}

// ---------- 엔드포인트 ----------

/** GET /api/v1/projects/{projectId}/campaigns — VIEWER↑ */
export async function listCampaigns(
  params: ListCampaignsParams,
): Promise<CampaignListResponse> {
  return ifMock<CampaignListResponse>(
    "campaign",
    async () => {
      const mockParams: CampaignListMockParams = {
        projectId: params.projectId,
        status: params.status,
        channel: params.channel,
        q: params.q,
        page: params.page,
        size: params.size,
        sort: params.sort,
      };
      return listCampaignsMock(mockParams);
    },
    async () => {
      const res = await api.get<CampaignListResponse>(
        `/api/v1/projects/${params.projectId}/campaigns`,
        {
          params: {
            status: params.status,
            channel: params.channel,
            q: params.q,
            page: params.page ?? 0,
            size: params.size ?? 20,
            sort: params.sort ?? "createdAt,desc",
          },
        },
      );
      return res.data;
    },
  );
}

/** GET /api/v1/projects/{projectId}/campaigns/{campaignId} — VIEWER↑ */
export async function getCampaign(
  projectId: number,
  campaignId: number,
): Promise<CampaignDetailMock> {
  return ifMock<CampaignDetailMock>(
    "campaign",
    async () => getCampaignMock(campaignId),
    async () => {
      const res = await api.get<CampaignDetailMock>(
        `/api/v1/projects/${projectId}/campaigns/${campaignId}`,
      );
      return res.data;
    },
  );
}

/** POST /api/v1/projects/{projectId}/campaigns — EDITOR↑ */
export async function createCampaign(
  req: CreateCampaignRequest,
): Promise<CampaignDetailMock> {
  return ifMock<CampaignDetailMock>(
    "campaign",
    async () => {
      const mockReq: CreateCampaignMockRequest = {
        projectId: req.projectId,
        campaignName: req.campaignName,
        campaignDescription: req.campaignDescription,
        campaignType: req.campaignType,
        channel: req.channel,
        segmentId: req.segmentId,
        messageTemplateId: req.messageTemplateId,
        schedule: req.schedule,
        isTempSave: req.isTempSave,
        meta: req.meta,
      };
      return createCampaignMock(mockReq);
    },
    async () => {
      const body = {
        campaignName: req.campaignName,
        campaignType: req.campaignType,
        campaignDescription: req.campaignDescription,
        channel: req.channel,
        segmentId: req.segmentId,
        messageTemplateId: req.messageTemplateId,
        ...scheduleToBackend(req.schedule),
        schedule: req.schedule, // backend가 어떤 형태든 양쪽 다 보냄
        isTempSave: req.isTempSave,
      };
      const res = await api.post<CampaignDetailMock>(
        `/api/v1/projects/${req.projectId}/campaigns`,
        body,
      );
      return res.data;
    },
  );
}

/** PUT /api/v1/projects/{projectId}/campaigns/{campaignId} — EDITOR↑ */
export async function updateCampaign(
  campaignId: number,
  req: UpdateCampaignRequest,
): Promise<CampaignDetailMock> {
  return ifMock<CampaignDetailMock>(
    "campaign",
    async () => {
      const mockReq: UpdateCampaignMockRequest = {
        projectId: req.projectId,
        campaignName: req.campaignName,
        campaignDescription: req.campaignDescription,
        campaignType: req.campaignType,
        channel: req.channel,
        segmentId: req.segmentId,
        messageTemplateId: req.messageTemplateId,
        schedule: req.schedule,
        isTempSave: req.isTempSave,
        meta: req.meta,
      };
      return updateCampaignMock(campaignId, mockReq);
    },
    async () => {
      const body = {
        campaignName: req.campaignName,
        campaignType: req.campaignType,
        campaignDescription: req.campaignDescription,
        channel: req.channel,
        segmentId: req.segmentId,
        messageTemplateId: req.messageTemplateId,
        ...scheduleToBackend(req.schedule),
        schedule: req.schedule,
        isTempSave: req.isTempSave,
      };
      const res = await api.put<CampaignDetailMock>(
        `/api/v1/projects/${req.projectId}/campaigns/${campaignId}`,
        body,
      );
      return res.data;
    },
  );
}

/** DELETE /api/v1/projects/{projectId}/campaigns/{campaignId} — EDITOR↑ (soft delete) */
export async function deleteCampaign(
  projectId: number,
  campaignId: number,
): Promise<DeleteCampaignResponse> {
  return ifMock<DeleteCampaignResponse>(
    "campaign",
    async () => deleteCampaignMock(campaignId),
    async () => {
      await api.delete(`/api/v1/projects/${projectId}/campaigns/${campaignId}`);
      return { campaignId };
    },
  );
}

/** POST /api/v1/projects/{projectId}/campaigns/{campaignId}/start — EDITOR↑ */
export async function startCampaign(
  projectId: number,
  campaignId: number,
): Promise<CampaignDetailMock> {
  return ifMock<CampaignDetailMock>(
    "campaign",
    async () => startCampaignMock(campaignId),
    async () => {
      const res = await api.post<CampaignDetailMock>(
        `/api/v1/projects/${projectId}/campaigns/${campaignId}/start`,
      );
      return res.data;
    },
  );
}

/** POST /api/v1/projects/{projectId}/campaigns/{campaignId}/pause — EDITOR↑ (P1) */
export async function pauseCampaign(
  projectId: number,
  campaignId: number,
): Promise<CampaignDetailMock> {
  return ifMock<CampaignDetailMock>(
    "campaign",
    async () => pauseCampaignMock(campaignId),
    async () => {
      const res = await api.post<CampaignDetailMock>(
        `/api/v1/projects/${projectId}/campaigns/${campaignId}/pause`,
      );
      return res.data;
    },
  );
}

// ---------- 도메인 enum re-export (편의) ----------
export type {
  CampaignStatus,
  CampaignType,
  Channel,
  SendMode,
  RepeatType,
  ExecutionStatus,
};
