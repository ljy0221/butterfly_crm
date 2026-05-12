import { api } from "./client";
import type { Channel } from "@/schemas/template";

/**
 * Sprint 4 — Message Template API.
 *
 * 백엔드: com.b101.butterfly.campaign.messagetemplate
 *  - presentation/MessageTemplateController.java
 *  - presentation/dto/MessageTemplate*Request.java / *Response.java
 *
 * 베이스 경로: `/api/v1/projects/{projectId}/templates`
 *
 * S14P31B101-233 머지 후 전 5 endpoint가 실 API. ApiResponse 래퍼는
 * client.ts 인터셉터가 자동 unwrap.
 */

// ---------- 요청 ----------

export interface MessageTemplateCreateRequest {
  messageTemplateName: string;
  messageTemplateDescription: string;
  channel: Channel;
  messageTemplateTitle: string;
  messageTemplateBody: string;
  messageTemplateImageUrl?: string;
}

export type MessageTemplateUpdateRequest = MessageTemplateCreateRequest;

export interface ListTemplatesParams {
  projectId: number;
  page?: number;
  size?: number;
  /** Spring `Pageable.sort` 형식. 예: "createdAt,desc". */
  sort?: string;
  /** UI용. 백엔드는 미수신 — 클라이언트 사이드 필터에 사용. */
  channel?: Channel | "ALL";
  /** UI용. 백엔드는 미수신 — 클라이언트 사이드 필터에 사용. */
  q?: string;
}

// ---------- 응답 ----------

export interface MessageTemplateCreateResponse {
  messageTemplateId: number;
}

/** 목록 아이템. 백엔드 `MessageTemplatesResponse` record 그대로. */
export interface MessageTemplateListItem {
  messageTemplateId: number;
  messageTemplateName: string;
  /** 백엔드 record 필드 이름이 `messageTitle` (Template 접두 없음). */
  messageTitle: string;
  channel: Channel;
  memberName: string;
  createdAt: string;
}

export interface PageInfo {
  totalElements: number;
  totalPages: number;
  currentPage: number;
  isLast: boolean;
}

export interface MessageTemplateListResponse {
  messageTemplates: MessageTemplateListItem[];
  pageInfo: PageInfo;
}

/** 상세. 백엔드 `MessageTemplateDetailResponse` record 그대로. */
export interface MessageTemplateDetailResponse {
  memberName: string;
  projectName: string;
  messageTemplateName: string;
  messageTemplateDescription: string;
  channel: Channel;
  messageTemplateTitle: string;
  messageTemplateBody: string;
  messageTemplateImageUrl: string | null;
  requiredVariables: string[];
  createdAt: string;
  updatedAt: string;
}

// ---------- 엔드포인트 ----------

/** POST /api/v1/projects/{projectId}/templates → `{ messageTemplateId }` (201). */
export async function createTemplate(
  projectId: number,
  req: MessageTemplateCreateRequest,
): Promise<MessageTemplateCreateResponse> {
  const res = await api.post<MessageTemplateCreateResponse>(
    `/api/v1/projects/${projectId}/templates`,
    req,
  );
  return res.data;
}

/** GET /api/v1/projects/{projectId}/templates */
export async function listTemplates(
  params: ListTemplatesParams,
): Promise<MessageTemplateListResponse> {
  const res = await api.get<MessageTemplateListResponse>(
    `/api/v1/projects/${params.projectId}/templates`,
    {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 12,
        sort: params.sort,
      },
    },
  );
  return res.data;
}

/** GET /api/v1/projects/{projectId}/templates/{templateId} */
export async function getTemplate(
  projectId: number,
  templateId: number,
): Promise<MessageTemplateDetailResponse> {
  const res = await api.get<MessageTemplateDetailResponse>(
    `/api/v1/projects/${projectId}/templates/${templateId}`,
  );
  return res.data;
}

/**
 * PUT /api/v1/projects/{projectId}/templates/{templateId} → `{ messageTemplateId }`.
 * 백엔드는 ID만 반환하므로 상세가 필요하면 호출 측에서 재조회한다.
 */
export async function updateTemplate(
  projectId: number,
  templateId: number,
  req: MessageTemplateUpdateRequest,
): Promise<MessageTemplateCreateResponse> {
  const res = await api.put<MessageTemplateCreateResponse>(
    `/api/v1/projects/${projectId}/templates/${templateId}`,
    req,
  );
  return res.data;
}

/** DELETE /api/v1/projects/{projectId}/templates/{templateId} → 204. */
export async function deleteTemplate(
  projectId: number,
  templateId: number,
): Promise<void> {
  await api.delete(`/api/v1/projects/${projectId}/templates/${templateId}`);
}
