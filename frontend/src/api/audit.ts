import { api, BASE_URL } from "./client";
import { useAuthStore } from "@/stores/auth";
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

/**
 * 감사 로그 CSV 다운로드 (S14P31B101-35 / S14P31B101-91).
 * GET /api/v1/audit-logs/export
 * axios 인터셉터가 text/csv를 ApiResponse로 오인하므로 raw fetch 사용.
 */
export async function exportAuditLogsCsv(params: AuditLogListParams = {}): Promise<void> {
  const state = useAuthStore.getState();
  const headers: Record<string, string> = {};
  const token = state.accessToken;
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (import.meta.env.DEV && state.member?.memberId != null) {
    headers["X-Member-Id"] = String(state.member.memberId);
  }

  const qs = new URLSearchParams();
  if (params.projectId != null) qs.set("projectId", String(params.projectId));
  if (params.memberId != null) qs.set("memberId", String(params.memberId));
  if (params.action) qs.set("action", params.action);
  if (params.targetType) qs.set("targetType", params.targetType);
  if (params.startDate) qs.set("startDate", params.startDate);
  if (params.endDate) qs.set("endDate", params.endDate);

  const res = await fetch(`${BASE_URL}/api/v1/audit-logs/export?${qs}`, { headers });
  if (!res.ok) throw new Error(`CSV 내보내기 실패 (HTTP ${res.status})`);

  const blob = await res.blob();
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = objectUrl;
  a.download = "audit-logs.csv";
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}
