import { useQuery } from "@tanstack/react-query";
import * as auditApi from "@/api/audit";
import type {
  AuditLogDetailResponse,
  AuditLogListItem,
  AuditLogListParams,
} from "@/api/audit";
import type { PageResponse } from "@/api/types";

/**
 * Sprint 7 — Audit React Query 훅.
 *
 * 캐시 키:
 *  - ["audit", "logs", params]
 *  - ["audit", "log", auditId]
 */

export const auditKeys = {
  all: ["audit"] as const,
  list: (params: AuditLogListParams) => ["audit", "logs", params] as const,
  detail: (auditId: number) => ["audit", "log", auditId] as const,
};

export function useAuditLogsQuery(
  params: AuditLogListParams,
  enabled: boolean = true,
) {
  return useQuery<PageResponse<AuditLogListItem>>({
    queryKey: auditKeys.list(params),
    queryFn: () => auditApi.listAuditLogs(params),
    enabled,
  });
}

export function useAuditLogDetailQuery(
  auditId: number | null,
  enabled: boolean = true,
) {
  return useQuery<AuditLogDetailResponse>({
    queryKey: auditKeys.detail(auditId ?? -1),
    queryFn: () => auditApi.getAuditLog(auditId as number),
    enabled: enabled && auditId != null,
  });
}
