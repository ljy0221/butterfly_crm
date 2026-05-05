import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as templateApi from "@/api/template";
import type {
  ListTemplatesParams,
  MessageTemplateCreateRequest,
  MessageTemplateCreateResponse,
  MessageTemplateDeleteResponse,
  MessageTemplateDetailResponse,
  MessageTemplateListResponse,
  MessageTemplateUpdateRequest,
} from "@/api/template";

/**
 * Sprint 4 — Message Template React Query 훅.
 *
 * 캐시 키:
 *  - ["templates", projectId, params]   목록
 *  - ["template", projectId, id]        상세
 *
 * mutation 성공 시 ["templates", projectId] prefix 매칭으로 invalidate.
 */

export type TemplateListQueryParams = Omit<ListTemplatesParams, "projectId">;

export const templateKeys = {
  all: ["templates"] as const,
  list: (projectId: number, params: TemplateListQueryParams) =>
    ["templates", projectId, params] as const,
  listsForProject: (projectId: number) => ["templates", projectId] as const,
  detail: (projectId: number, id: number) =>
    ["template", projectId, id] as const,
};

export function useTemplateListQuery(
  projectId: number | null,
  params: TemplateListQueryParams,
  enabled: boolean = true,
) {
  return useQuery<MessageTemplateListResponse>({
    queryKey: templateKeys.list(projectId ?? -1, params),
    queryFn: () =>
      templateApi.listTemplates({
        projectId: projectId as number,
        ...params,
      }),
    enabled: enabled && projectId != null,
  });
}

export function useTemplateDetailQuery(
  projectId: number | null,
  id: number | null,
  enabled: boolean = true,
) {
  return useQuery<MessageTemplateDetailResponse>({
    queryKey: templateKeys.detail(projectId ?? -1, id ?? -1),
    queryFn: () => templateApi.getTemplate(id as number),
    enabled: enabled && projectId != null && id != null,
  });
}

export interface CreateTemplateVariables {
  req: MessageTemplateCreateRequest;
  meta: {
    description?: string;
    title?: string;
    createdBy: string;
  };
}

export function useCreateTemplateMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<
    MessageTemplateCreateResponse,
    unknown,
    CreateTemplateVariables
  >({
    mutationFn: ({ req, meta }) => templateApi.createTemplate(req, meta),
    onSuccess: () => {
      qc.invalidateQueries({
        queryKey: templateKeys.listsForProject(projectId),
      });
    },
  });
}

export interface UpdateTemplateVariables {
  id: number;
  req: MessageTemplateUpdateRequest;
}

export function useUpdateTemplateMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<
    MessageTemplateDetailResponse,
    unknown,
    UpdateTemplateVariables
  >({
    mutationFn: ({ id, req }) => templateApi.updateTemplate(id, req),
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({
        queryKey: templateKeys.listsForProject(projectId),
      });
      qc.invalidateQueries({
        queryKey: templateKeys.detail(projectId, id),
      });
    },
  });
}

export function useDeleteTemplateMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<MessageTemplateDeleteResponse, unknown, number>({
    mutationFn: (id) => templateApi.deleteTemplate(id),
    onSuccess: (_data, id) => {
      qc.invalidateQueries({
        queryKey: templateKeys.listsForProject(projectId),
      });
      qc.invalidateQueries({
        queryKey: templateKeys.detail(projectId, id),
      });
    },
  });
}
