import { api } from "./client";
import { ifMock } from "./_mock";
import type { Channel, TemplateStatus } from "@/schemas/template";
import {
  deleteTemplateMock,
  getTemplateMock,
  listTemplatesMock,
  mockUpsertTemplate,
  updateTemplateMock,
  type TemplateListMockParams,
  type TemplateListMockResponse,
  type TemplateRecord,
  type TemplateSummary,
} from "./_mock/template";

/**
 * Sprint 4 — Message Template API.
 *
 * 백엔드: com.b101.butterfly.campaign.messagetemplate
 *  - presentation/MessageTemplateController.java
 *  - presentation/dto/MessageTemplate{Create}Request.java
 *  - presentation/dto/MessageTemplate{Create}Response.java
 *
 * 구현 상태:
 *  - POST /api/v1/templates  ✅ 실 API
 *  - GET  /api/v1/templates  ❌ mock-fallback (`template_list:true`)
 *  - GET  /api/v1/templates/{id} ❌ mock-fallback
 *  - PUT  /api/v1/templates/{id} ❌ mock-fallback
 *  - DELETE /api/v1/templates/{id} ❌ mock-fallback
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap 한다.
 */

// ---------- 백엔드 record 그대로 ----------

export interface MessageTemplateCreateRequest {
  projectId: number;
  messageTemplateName: string;
  channel: Channel;
  messageTemplateBody: string;
}

export interface MessageTemplateCreateResponse {
  messageTemplateId: number;
}

// ---------- 목록/상세 응답 (제안 시그니처 — backend liaison 4-template/api.md §4) ----------

export interface MessageTemplateListItem {
  messageTemplateId: number;
  messageTemplateName: string;
  channel: Channel;
  messageTemplateBody: string;
  description?: string;
  status: TemplateStatus;
  createdBy: string;
  createdAt: string;
}

export interface MessageTemplateListResponse {
  templates: MessageTemplateListItem[];
  totalPages: number;
  totalElements: number;
  currentPage: number;
}

export interface MessageTemplateDetailResponse {
  messageTemplateId: number;
  projectId: number;
  messageTemplateName: string;
  channel: Channel;
  messageTemplateBody: string;
  requiredVariables: string[];
  status: TemplateStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  description?: string;
  title?: string;
}

export interface MessageTemplateUpdateRequest {
  messageTemplateName: string;
  channel: Channel;
  messageTemplateBody: string;
  // mock-only 보조 필드
  description?: string;
  title?: string;
}

export interface MessageTemplateDeleteResponse {
  messageTemplateId: number;
}

export interface ListTemplatesParams {
  projectId: number;
  page?: number;
  size?: number;
  channel?: Channel | "ALL";
  q?: string;
  sort?: "createdAt,desc" | "createdAt,asc" | "name,asc";
}

// ---------- 어댑터 변환 ----------

function recordToDetail(rec: TemplateRecord): MessageTemplateDetailResponse {
  return {
    messageTemplateId: rec.messageTemplateId,
    projectId: rec.projectId,
    messageTemplateName: rec.messageTemplateName,
    channel: rec.channel,
    messageTemplateBody: rec.messageTemplateBody,
    requiredVariables: rec.requiredVariables,
    status: rec.status,
    createdBy: rec.createdBy,
    createdAt: rec.createdAt,
    updatedAt: rec.updatedAt,
    description: rec.description,
    title: rec.title,
  };
}

function summaryToListItem(s: TemplateSummary): MessageTemplateListItem {
  return {
    messageTemplateId: s.messageTemplateId,
    messageTemplateName: s.messageTemplateName,
    channel: s.channel,
    messageTemplateBody: s.messageTemplateBody,
    description: s.description,
    status: s.status,
    createdBy: s.createdBy,
    createdAt: s.createdAt,
  };
}

// ---------- 엔드포인트 ----------

/**
 * 생성 — 실 API.
 *
 * 백엔드는 `messageTemplateName / channel / messageTemplateBody / projectId` 만 받고
 * `messageTemplateId` 만 반환한다. description/title 등 보조 필드는 mock store에 봉합 저장한다.
 *
 * @param req         POST 페이로드 (백엔드 record 와 동일 필드명)
 * @param meta        mock 봉합용 보조 정보 (description/title/createdBy)
 */
export async function createTemplate(
  req: MessageTemplateCreateRequest,
  meta: {
    description?: string;
    title?: string;
    createdBy: string;
  },
): Promise<MessageTemplateCreateResponse> {
  const res = await api.post<MessageTemplateCreateResponse>(
    "/api/v1/templates",
    req,
  );
  const data = res.data;
  // 응답 받은 messageTemplateId로 mock store 봉합
  // (GET /templates/{id} 가 미구현 상태이므로 mock fallback에서 일관성 있게 보이도록)
  mockUpsertTemplate(data.messageTemplateId, {
    projectId: req.projectId,
    messageTemplateName: req.messageTemplateName,
    channel: req.channel,
    messageTemplateBody: req.messageTemplateBody,
    description: meta.description,
    title: meta.title,
    createdBy: meta.createdBy,
  });
  return data;
}

/**
 * 목록 — 미구현 시 mock-fallback.
 * 백엔드 구현되면 `ifMock` 분기에서 real 경로가 활성화된다.
 */
export async function listTemplates(
  params: ListTemplatesParams,
): Promise<MessageTemplateListResponse> {
  return ifMock<MessageTemplateListResponse>(
    "template_list",
    async () => {
      const mockParams: TemplateListMockParams = {
        projectId: params.projectId,
        page: params.page,
        size: params.size,
        channel: params.channel,
        q: params.q,
        sort: params.sort,
      };
      const res: TemplateListMockResponse = await listTemplatesMock(mockParams);
      return {
        templates: res.templates.map(summaryToListItem),
        totalElements: res.totalElements,
        totalPages: res.totalPages,
        currentPage: res.currentPage,
      };
    },
    async () => {
      const res = await api.get<MessageTemplateListResponse>("/api/v1/templates", {
        params: {
          projectId: params.projectId,
          page: params.page ?? 0,
          size: params.size ?? 12,
          channel: params.channel === "ALL" ? undefined : params.channel,
          q: params.q,
          sort: params.sort ?? "createdAt,desc",
        },
      });
      return res.data;
    },
  );
}

/** 상세 — 미구현 시 mock-fallback. */
export async function getTemplate(
  id: number,
): Promise<MessageTemplateDetailResponse> {
  return ifMock<MessageTemplateDetailResponse>(
    "template_list",
    async () => recordToDetail(await getTemplateMock(id)),
    async () => {
      const res = await api.get<MessageTemplateDetailResponse>(
        `/api/v1/templates/${id}`,
      );
      return res.data;
    },
  );
}

/** 수정 — 미구현 시 mock-fallback. */
export async function updateTemplate(
  id: number,
  req: MessageTemplateUpdateRequest,
): Promise<MessageTemplateDetailResponse> {
  return ifMock<MessageTemplateDetailResponse>(
    "template_list",
    async () =>
      recordToDetail(
        await updateTemplateMock(id, {
          messageTemplateName: req.messageTemplateName,
          channel: req.channel,
          messageTemplateBody: req.messageTemplateBody,
          description: req.description,
          title: req.title,
        }),
      ),
    async () => {
      const res = await api.put<MessageTemplateDetailResponse>(
        `/api/v1/templates/${id}`,
        {
          messageTemplateName: req.messageTemplateName,
          channel: req.channel,
          messageTemplateBody: req.messageTemplateBody,
        },
      );
      return res.data;
    },
  );
}

/** 삭제 — 미구현 시 mock-fallback. */
export async function deleteTemplate(
  id: number,
): Promise<MessageTemplateDeleteResponse> {
  return ifMock<MessageTemplateDeleteResponse>(
    "template_list",
    async () => deleteTemplateMock(id),
    async () => {
      const res = await api.delete<MessageTemplateDeleteResponse>(
        `/api/v1/templates/${id}`,
      );
      return res.data;
    },
  );
}
