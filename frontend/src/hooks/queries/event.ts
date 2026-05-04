import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as eventApi from "@/api/event";
import type {
  CreateCategoryRequest,
  EventCategoryRecord,
  EventCategorySummary,
  PropertyRecord,
  RecentSample,
} from "@/api/event";

/**
 * Sprint 7 — Event React Query 훅.
 *
 * 캐시 키:
 *  - ["events", projectId, "categories"]
 *  - ["events", projectId, "category", categoryName]
 *  - ["events", projectId, "schema", categoryName]
 *  - ["events", projectId, "sample", categoryName]
 */

export const eventKeys = {
  all: (projectId: number) => ["events", projectId] as const,
  categories: (projectId: number) =>
    ["events", projectId, "categories"] as const,
  category: (projectId: number, categoryName: string) =>
    ["events", projectId, "category", categoryName] as const,
  schema: (projectId: number, categoryName: string) =>
    ["events", projectId, "schema", categoryName] as const,
  sample: (projectId: number, categoryName: string) =>
    ["events", projectId, "sample", categoryName] as const,
};

export function useEventCategoriesQuery(
  projectId: number | null,
  enabled: boolean = true,
) {
  return useQuery<EventCategorySummary[]>({
    queryKey: eventKeys.categories(projectId ?? -1),
    queryFn: () => eventApi.listCategories(projectId as number),
    enabled: enabled && projectId != null,
  });
}

export function useEventDetailQuery(
  projectId: number | null,
  categoryName: string | null,
  enabled: boolean = true,
) {
  return useQuery<EventCategoryRecord>({
    queryKey: eventKeys.category(projectId ?? -1, categoryName ?? ""),
    queryFn: () =>
      eventApi.getCategory(projectId as number, categoryName as string),
    enabled: enabled && projectId != null && !!categoryName,
  });
}

export function useEventSchemaQuery(
  projectId: number | null,
  categoryName: string | null,
  enabled: boolean = true,
) {
  return useQuery<PropertyRecord[]>({
    queryKey: eventKeys.schema(projectId ?? -1, categoryName ?? ""),
    queryFn: () =>
      eventApi.getSchema(projectId as number, categoryName as string),
    enabled: enabled && projectId != null && !!categoryName,
  });
}

export function useEventSampleQuery(
  projectId: number | null,
  categoryName: string | null,
  enabled: boolean = true,
) {
  return useQuery<RecentSample | null>({
    queryKey: eventKeys.sample(projectId ?? -1, categoryName ?? ""),
    queryFn: () =>
      eventApi.getRecentSample(projectId as number, categoryName as string),
    enabled: enabled && projectId != null && !!categoryName,
  });
}

// ---------- Mutations ----------

export function useCreateCategoryMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<EventCategoryRecord, unknown, CreateCategoryRequest>({
    mutationFn: (req) => eventApi.createCategory(projectId, req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: eventKeys.categories(projectId) });
    },
  });
}

export interface UpdateSchemaVariables {
  categoryName: string;
  properties: PropertyRecord[];
}

export function useUpdateSchemaMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<PropertyRecord[], unknown, UpdateSchemaVariables>({
    mutationFn: ({ categoryName, properties }) =>
      eventApi.updateSchema(projectId, categoryName, properties),
    onSuccess: (_data, { categoryName }) => {
      qc.invalidateQueries({
        queryKey: eventKeys.schema(projectId, categoryName),
      });
      qc.invalidateQueries({
        queryKey: eventKeys.category(projectId, categoryName),
      });
      qc.invalidateQueries({ queryKey: eventKeys.categories(projectId) });
    },
  });
}

export function useDeleteCategoryMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<{ categoryId: number }, unknown, number>({
    mutationFn: (categoryId) => eventApi.deleteCategory(projectId, categoryId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: eventKeys.categories(projectId) });
    },
  });
}
