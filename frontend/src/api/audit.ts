import { api } from "./client";
import type { PageResponse } from "./types";

/**
 * Sprint 7 — Audit API.
 *
 * 백엔드 (실 API 2개):
 *  - GET /api/v1/audit-logs                  (목록, 필터 + 페이징)
 *  - GET /api/v1/audit-logs/{auditId}        (상세)
 *
 * 권한: OWNER↑ (또는 ADMIN). 컨트롤러 레벨 @PreAuthorize.
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap.
 */

export interface AuditLogListItem {
  auditId: number;
  projectId: number | null;
  memberId: number;
  /** 백엔드 MemberLookupPort 도입 전까지 null. */
  memberName: string | null;
  action: string;
  targetType: string;
  targetId: number | null;
  ipAddress: string | null;
  userAgent: string | null;
  /** ISO LocalDateTime */
  createdAt: string;
}

export interface AuditLogDetailResponse {
  auditId: number;
  projectId: number | null;
  memberId: number;
  memberName: string | null;
  memberEmail: string | null;
  action: string;
  targetType: string;
  targetId: number | null;
  /** Map<String,Object> */
  detail: Record<string, unknown> | null;
  ipAddress: string | null;
  userAgent: string | null;
  createdAt: string;
}

export interface AuditLogListParams {
  projectId?: number;
  memberId?: number;
  action?: string;
  targetType?: string;
  /** YYYY-MM-DD */
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}

export async function listAuditLogs(
  params: AuditLogListParams = {},
): Promise<PageResponse<AuditLogListItem>> {
  const res = await api.get<PageResponse<AuditLogListItem>>(
    "/api/v1/audit-logs",
    {
      params: {
        projectId: params.projectId,
        memberId: params.memberId,
        action: params.action,
        targetType: params.targetType,
        startDate: params.startDate,
        endDate: params.endDate,
        page: params.page ?? 0,
        size: params.size ?? 20,
      },
    },
  );
  return res.data;
}

export async function getAuditLog(
  auditId: number,
): Promise<AuditLogDetailResponse> {
  const res = await api.get<AuditLogDetailResponse>(
    `/api/v1/audit-logs/${auditId}`,
  );
  return res.data;
}
