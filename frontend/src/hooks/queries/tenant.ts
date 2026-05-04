import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as tenantApi from "@/api/tenant";
import type {
  ApiKeyResponse,
  ProjectMemberResponse,
  ProjectResponse,
  UpdateMemberRoleRequest,
} from "@/api/tenant";

/**
 * Sprint 2 — Tenant 도메인 React Query 훅.
 *
 * 캐시 키:
 *  - ["project", projectId]
 *  - ["project", projectId, "members"]
 *
 * mutation 성공 시 ["project", projectId] prefix 매칭으로 invalidate.
 */

export const tenantKeys = {
  all: ["project"] as const,
  detail: (projectId: number) => ["project", projectId] as const,
  members: (projectId: number) =>
    ["project", projectId, "members"] as const,
};

export function useProjectQuery(
  projectId: number | null,
  enabled: boolean = true,
) {
  return useQuery<ProjectResponse>({
    queryKey: tenantKeys.detail(projectId ?? -1),
    queryFn: () => tenantApi.getProject(projectId as number),
    enabled: enabled && projectId != null,
  });
}

export function useMembersQuery(
  projectId: number | null,
  enabled: boolean = true,
) {
  return useQuery<ProjectMemberResponse[]>({
    queryKey: tenantKeys.members(projectId ?? -1),
    queryFn: () => tenantApi.listMembers(projectId as number),
    enabled: enabled && projectId != null,
  });
}

export function useUpdateMemberRoleMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<
    void,
    unknown,
    { memberId: number; req: UpdateMemberRoleRequest }
  >({
    mutationFn: ({ memberId, req }) =>
      tenantApi.updateMemberRole(projectId, memberId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: tenantKeys.members(projectId) });
    },
  });
}

export function useDeleteMemberMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<void, unknown, number>({
    mutationFn: (memberId) => tenantApi.deleteMember(projectId, memberId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: tenantKeys.members(projectId) });
    },
  });
}

export function useIssueApiKeyMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<ApiKeyResponse, unknown, void>({
    mutationFn: () => tenantApi.issueApiKey(projectId),
    onSuccess: () => {
      // hasApiKey 필드가 변경되므로 프로젝트 상세 invalidate
      qc.invalidateQueries({ queryKey: tenantKeys.detail(projectId) });
    },
  });
}

export function useRevokeApiKeyMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<ApiKeyResponse, unknown, void>({
    mutationFn: () => tenantApi.revokeApiKey(projectId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: tenantKeys.detail(projectId) });
    },
  });
}
