import { api } from "./client";
import { ifMock } from "./_mock";
import {
  createCategoryMock,
  deleteCategoryMock,
  getCategoryMock,
  getSchemaMock,
  listCategoriesMock,
  recentSampleMock,
  updateSchemaMock,
  type EventCategoryRecord,
  type EventCategorySummary,
  type PropertyRecord,
} from "./_mock/event";

/**
 * Sprint 7 — Event API.
 *
 * 백엔드 (실 API):
 *  - POST /api/v1/events  (EventCollectController)
 *  - GET    /api/v1/projects/{projectId}/events/categories
 *  - GET    /api/v1/projects/{projectId}/events/categories/{categoryName}
 *  - GET    /api/v1/projects/{projectId}/events/categories/{categoryName}/schema
 *  - POST   /api/v1/projects/{projectId}/events/categories
 *  - PUT    /api/v1/projects/{projectId}/events/categories/{categoryName}/schema
 *  - DELETE /api/v1/projects/{projectId}/events/categories/{categoryId}
 *  - GET    /api/v1/projects/{projectId}/events/categories/{categoryName}/sample
 */

// ---------- Re-exports ----------

export type {
  EventCategoryRecord,
  EventCategorySummary,
  PropertyRecord,
};

// ---------- POST /api/v1/events (실 API) ----------

export interface EventCollectRequest {
  /** externalId 등 비숫자 식별자가 들어올 수 있어 string. 백엔드 record도 String. */
  userId: string;
  deviceId: string;
  eventCategory: string;
  /** ISO LocalDateTime 문자열 ("YYYY-MM-DDTHH:mm:ss"). */
  occurredAt: string;
  eventData?: unknown;
}

export interface EventCollectResponse {
  eventId: string;
  acceptedAt: string;
  schemaVersion: number;
}

/**
 * 단일 이벤트 수집. SDK 가 호출. 운영 콘솔에서는 보통 미사용.
 * X-Project-Id 헤더는 인터셉터가 자동 첨부하지 않으므로 호출 측이 직접 전달한다.
 */
export async function collectEvent(
  projectId: number,
  req: EventCollectRequest,
): Promise<EventCollectResponse> {
  const res = await api.post<EventCollectResponse>("/api/v1/events", req, {
    headers: { "X-Project-Id": String(projectId) },
  });
  return res.data;
}

// ---------- 카테고리 / 스키마 (mock-fallback) ----------

export async function listCategories(
  projectId: number,
): Promise<EventCategorySummary[]> {
  return ifMock<EventCategorySummary[]>(
    "event_categories",
    () => listCategoriesMock(projectId),
    async () => {
      const res = await api.get<EventCategorySummary[]>(
        `/api/v1/projects/${projectId}/events/categories`,
      );
      return res.data;
    },
  );
}

export async function getCategory(
  projectId: number,
  categoryName: string,
): Promise<EventCategoryRecord> {
  return ifMock<EventCategoryRecord>(
    "event_categories",
    () => getCategoryMock(projectId, categoryName),
    async () => {
      const res = await api.get<EventCategoryRecord>(
        `/api/v1/projects/${projectId}/events/categories/${encodeURIComponent(categoryName)}`,
      );
      return res.data;
    },
  );
}

export async function getSchema(
  projectId: number,
  categoryName: string,
): Promise<PropertyRecord[]> {
  return ifMock<PropertyRecord[]>(
    "event_schema",
    () => getSchemaMock(projectId, categoryName),
    async () => {
      const res = await api.get<PropertyRecord[]>(
        `/api/v1/projects/${projectId}/events/categories/${encodeURIComponent(categoryName)}/schema`,
      );
      return res.data;
    },
  );
}

export interface CreateCategoryRequest {
  categoryName: string;
  displayName: string;
  description?: string;
  properties?: PropertyRecord[];
}

export async function createCategory(
  projectId: number,
  req: CreateCategoryRequest,
): Promise<EventCategoryRecord> {
  return ifMock<EventCategoryRecord>(
    "event_categories",
    () =>
      createCategoryMock({
        projectId,
        categoryName: req.categoryName,
        displayName: req.displayName,
        description: req.description,
        properties: req.properties,
      }),
    async () => {
      const res = await api.post<EventCategoryRecord>(
        `/api/v1/projects/${projectId}/events/categories`,
        req,
      );
      return res.data;
    },
  );
}

export async function updateSchema(
  projectId: number,
  categoryName: string,
  properties: PropertyRecord[],
): Promise<PropertyRecord[]> {
  return ifMock<PropertyRecord[]>(
    "event_schema",
    () => updateSchemaMock(projectId, categoryName, properties),
    async () => {
      const res = await api.put<PropertyRecord[]>(
        `/api/v1/projects/${projectId}/events/categories/${encodeURIComponent(categoryName)}/schema`,
        { properties },
      );
      return res.data;
    },
  );
}

export async function deleteCategory(
  projectId: number,
  categoryId: number,
): Promise<{ categoryId: number }> {
  return ifMock<{ categoryId: number }>(
    "event_categories",
    () => deleteCategoryMock(projectId, categoryId),
    async () => {
      await api.delete(
        `/api/v1/projects/${projectId}/events/categories/${categoryId}`,
      );
      return { categoryId };
    },
  );
}

export interface RecentSample {
  occurredAt: string;
  eventData: Record<string, unknown>;
}

export async function getRecentSample(
  projectId: number,
  categoryName: string,
): Promise<RecentSample | null> {
  return ifMock<RecentSample | null>(
    "event_schema",
    () => recentSampleMock(projectId, categoryName),
    async () => {
      const res = await api.get<RecentSample | null>(
        `/api/v1/projects/${projectId}/events/categories/${encodeURIComponent(categoryName)}/sample`,
      );
      return res.data;
    },
  );
}
