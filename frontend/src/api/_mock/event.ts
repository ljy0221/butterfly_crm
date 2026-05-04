import { delay } from "./index";
import type { CategoryKind, PropertyType } from "@/schemas/event";

/**
 * Sprint 7 — Event 카테고리/스키마 mock store.
 *
 * 백엔드 GET/POST/PUT/DELETE 미구현 시 사용하는 in-memory 어댑터.
 *  - Map<categoryId, EventCategoryRecord>
 *  - Map<categoryName, PropertyRecord[]>
 *
 * 5종 시드: purchase(7), add_to_cart(4), page_view(3), login(2), signup(3).
 * 카테고리별 임의 eventCount(50K~200K), lastCollectedAt(최근 24h 안).
 */

// ---------- 도메인 모델 ----------

export interface EventCategoryRecord {
  categoryId: number;
  projectId: number;
  categoryName: string;
  displayName: string;
  description: string;
  kind: CategoryKind;
  /** 30일 누적 수집량(임의) */
  eventCount: number;
  /** 0~100, mock 임의 */
  successRate: number;
  /** 최근 1건 ISO. null 가능. */
  lastCollectedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PropertyRecord {
  key: string;
  type: PropertyType;
  required: boolean;
  example?: string;
}

export interface EventCategorySummary {
  categoryId: number;
  categoryName: string;
  displayName: string;
  description: string;
  kind: CategoryKind;
  /** 속성 갯수 */
  propertyCount: number;
  eventCount: number;
  successRate: number;
  lastCollectedAt: string | null;
}

// ---------- 싱글톤 store ----------

const categories = new Map<number, EventCategoryRecord>();
const schemas = new Map<string, PropertyRecord[]>();
let nextId = 1;
let seeded = false;

function nowMinusHours(h: number): string {
  const d = new Date();
  d.setHours(d.getHours() - h);
  return d.toISOString();
}

function rand(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function seed(): void {
  if (seeded) return;
  seeded = true;

  type SeedCat = {
    categoryName: string;
    displayName: string;
    description: string;
    kind: CategoryKind;
    properties: PropertyRecord[];
  };

  const seeds: SeedCat[] = [
    {
      categoryName: "purchase",
      displayName: "구매 완료",
      description: "사용자가 결제를 완료했음",
      kind: "BUILTIN",
      properties: [
        { key: "order_id", type: "string", required: true, example: "SO-2026-041829" },
        { key: "total_amount", type: "number", required: true, example: "184000" },
        { key: "currency", type: "string", required: true, example: "KRW" },
        { key: "payment_method", type: "string", required: false, example: "card" },
        { key: "is_first_order", type: "boolean", required: false, example: "true" },
        { key: "items_count", type: "number", required: false, example: "3" },
        { key: "purchased_at", type: "datetime", required: false, example: "2026-05-02T14:30:00Z" },
      ],
    },
    {
      categoryName: "add_to_cart",
      displayName: "장바구니 담기",
      description: "사용자가 상품을 장바구니에 담음",
      kind: "BUILTIN",
      properties: [
        { key: "product_id", type: "string", required: true, example: "SKU-12345" },
        { key: "product_name", type: "string", required: true, example: "버터플라이 쿠션" },
        { key: "price", type: "number", required: true, example: "29900" },
        { key: "quantity", type: "number", required: false, example: "1" },
      ],
    },
    {
      categoryName: "page_view",
      displayName: "페이지 조회",
      description: "사용자가 특정 페이지를 조회함",
      kind: "BUILTIN",
      properties: [
        { key: "page_url", type: "string", required: true, example: "/products/123" },
        { key: "referrer", type: "string", required: false, example: "https://google.com" },
        { key: "duration_ms", type: "number", required: false, example: "8500" },
      ],
    },
    {
      categoryName: "login",
      displayName: "로그인",
      description: "사용자가 로그인 성공",
      kind: "BUILTIN",
      properties: [
        { key: "method", type: "string", required: true, example: "email" },
        { key: "is_new_device", type: "boolean", required: false, example: "false" },
      ],
    },
    {
      categoryName: "signup",
      displayName: "가입 완료",
      description: "신규 회원이 가입했음",
      kind: "BUILTIN",
      properties: [
        { key: "method", type: "string", required: true, example: "email" },
        { key: "referral_code", type: "string", required: false, example: "WELCOME2026" },
        { key: "is_marketing_opted", type: "boolean", required: false, example: "true" },
      ],
    },
  ];

  for (const s of seeds) {
    const id = nextId++;
    const now = new Date();
    const createdAt = new Date(now.getTime() - rand(7, 60) * 86400000).toISOString();
    categories.set(id, {
      categoryId: id,
      projectId: 1,
      categoryName: s.categoryName,
      displayName: s.displayName,
      description: s.description,
      kind: s.kind,
      eventCount: rand(50_000, 200_000),
      successRate: 95 + Math.random() * 5,
      lastCollectedAt: nowMinusHours(rand(0, 24)),
      createdAt,
      updatedAt: createdAt,
    });
    schemas.set(s.categoryName, [...s.properties]);
  }
}

// ---------- 외부 API ----------

function toSummary(rec: EventCategoryRecord): EventCategorySummary {
  return {
    categoryId: rec.categoryId,
    categoryName: rec.categoryName,
    displayName: rec.displayName,
    description: rec.description,
    kind: rec.kind,
    propertyCount: schemas.get(rec.categoryName)?.length ?? 0,
    eventCount: rec.eventCount,
    successRate: rec.successRate,
    lastCollectedAt: rec.lastCollectedAt,
  };
}

export async function listCategoriesMock(
  projectId: number,
): Promise<EventCategorySummary[]> {
  seed();
  const all = Array.from(categories.values())
    .filter((c) => c.projectId === projectId)
    .map(toSummary)
    .sort((a, b) => a.categoryName.localeCompare(b.categoryName));
  return delay(all, 200);
}

export async function getCategoryMock(
  projectId: number,
  categoryName: string,
): Promise<EventCategoryRecord> {
  seed();
  const rec = Array.from(categories.values()).find(
    (c) => c.projectId === projectId && c.categoryName === categoryName,
  );
  if (!rec) {
    throw Object.assign(new Error("등록되지 않은 이벤트 카테고리입니다"), {
      code: "CATEGORY_NOT_FOUND",
    });
  }
  return delay({ ...rec }, 180);
}

export async function getSchemaMock(
  projectId: number,
  categoryName: string,
): Promise<PropertyRecord[]> {
  seed();
  const rec = Array.from(categories.values()).find(
    (c) => c.projectId === projectId && c.categoryName === categoryName,
  );
  if (!rec) {
    throw Object.assign(new Error("등록되지 않은 이벤트 카테고리입니다"), {
      code: "CATEGORY_NOT_FOUND",
    });
  }
  return delay([...(schemas.get(categoryName) ?? [])], 180);
}

export interface CreateCategoryMockRequest {
  projectId: number;
  categoryName: string;
  displayName: string;
  description?: string;
  properties?: PropertyRecord[];
}

export async function createCategoryMock(
  req: CreateCategoryMockRequest,
): Promise<EventCategoryRecord> {
  seed();
  if (
    Array.from(categories.values()).some(
      (c) =>
        c.projectId === req.projectId && c.categoryName === req.categoryName,
    )
  ) {
    throw Object.assign(new Error("이미 등록된 이벤트 카테고리입니다"), {
      code: "CATEGORY_DUPLICATE",
    });
  }
  const id = nextId++;
  const now = new Date().toISOString();
  const rec: EventCategoryRecord = {
    categoryId: id,
    projectId: req.projectId,
    categoryName: req.categoryName,
    displayName: req.displayName,
    description: req.description ?? "",
    kind: "CUSTOM",
    eventCount: 0,
    successRate: 100,
    lastCollectedAt: null,
    createdAt: now,
    updatedAt: now,
  };
  categories.set(id, rec);
  schemas.set(req.categoryName, [...(req.properties ?? [])]);
  return delay({ ...rec }, 220);
}

export async function updateSchemaMock(
  projectId: number,
  categoryName: string,
  properties: PropertyRecord[],
): Promise<PropertyRecord[]> {
  seed();
  const rec = Array.from(categories.values()).find(
    (c) => c.projectId === projectId && c.categoryName === categoryName,
  );
  if (!rec) {
    throw Object.assign(new Error("등록되지 않은 이벤트 카테고리입니다"), {
      code: "CATEGORY_NOT_FOUND",
    });
  }
  // 키 중복 체크
  const seen = new Set<string>();
  for (const p of properties) {
    if (seen.has(p.key)) {
      throw Object.assign(new Error(`속성 키가 중복됩니다: ${p.key}`), {
        code: "PROPERTY_DUPLICATE",
      });
    }
    seen.add(p.key);
  }
  schemas.set(categoryName, [...properties]);
  rec.updatedAt = new Date().toISOString();
  categories.set(rec.categoryId, rec);
  return delay([...properties], 200);
}

export async function deleteCategoryMock(
  projectId: number,
  categoryId: number,
): Promise<{ categoryId: number }> {
  seed();
  const rec = categories.get(categoryId);
  if (!rec || rec.projectId !== projectId) {
    throw Object.assign(new Error("등록되지 않은 이벤트 카테고리입니다"), {
      code: "CATEGORY_NOT_FOUND",
    });
  }
  if (rec.kind === "BUILTIN") {
    throw Object.assign(new Error("기본 이벤트 카테고리는 삭제할 수 없습니다"), {
      code: "BUILTIN_NOT_DELETABLE",
    });
  }
  categories.delete(categoryId);
  schemas.delete(rec.categoryName);
  return delay({ categoryId }, 180);
}

/** 최근 수신 샘플 — eventData 임의 생성. */
export async function recentSampleMock(
  projectId: number,
  categoryName: string,
): Promise<{ occurredAt: string; eventData: Record<string, unknown> } | null> {
  seed();
  const props = schemas.get(categoryName);
  if (!props) return delay(null, 100);
  const sample: Record<string, unknown> = {};
  for (const p of props) {
    if (p.type === "string") sample[p.key] = p.example ?? "sample";
    else if (p.type === "number") sample[p.key] = Number(p.example ?? rand(1, 1000));
    else if (p.type === "boolean") sample[p.key] = p.example === "true";
    else if (p.type === "datetime") sample[p.key] = p.example ?? new Date().toISOString();
    else sample[p.key] = {};
  }
  void projectId;
  return delay(
    { occurredAt: nowMinusHours(rand(0, 6)), eventData: sample },
    150,
  );
}
