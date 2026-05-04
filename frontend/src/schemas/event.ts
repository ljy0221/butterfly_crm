import { z } from "zod";

/**
 * Sprint 7 — Event 도메인 Zod 스키마.
 *
 * 백엔드:
 *  - POST /api/v1/events  (EventCollectRequest record)
 *
 * 카테고리/스키마는 mock-fallback. 백엔드 미구현이라 frontend 측에서만 정의.
 *
 * categoryName 정규식 ^[a-z_][a-z0-9_]{2,39}$ 는 SDK 트래킹 키 컨벤션.
 * propertyKey 는 `event_data` JSON object 의 1-depth key 컨벤션.
 */

// ---------- 도메인 enum ----------

export const PropertyTypeSchema = z.enum([
  "string",
  "number",
  "boolean",
  "datetime",
  "object",
]);
export type PropertyType = z.infer<typeof PropertyTypeSchema>;

export const CategoryKindSchema = z.enum(["BUILTIN", "CUSTOM"]);
export type CategoryKind = z.infer<typeof CategoryKindSchema>;

// ---------- 필드 단위 스키마 ----------

export const categoryNameSchema = z
  .string()
  .min(1, "이벤트명은 필수입니다")
  .regex(
    /^[a-z_][a-z0-9_]{2,39}$/,
    "영소문자·숫자·언더스코어 3~40자 (선두는 소문자/언더스코어)",
  );

export const displayNameSchema = z
  .string()
  .min(1, "표시명은 필수입니다")
  .max(50, "표시명은 50자 이내여야 합니다");

export const descriptionSchema = z
  .string()
  .max(200, "설명은 200자 이내여야 합니다")
  .optional();

export const propertyKeySchema = z
  .string()
  .min(1, "속성명은 필수입니다")
  .regex(
    /^[a-z_][a-z0-9_]{0,39}$/,
    "영소문자·숫자·언더스코어 1~40자",
  );

// ---------- 폼/구조 스키마 ----------

export const propertyRowSchema = z.object({
  key: propertyKeySchema,
  type: PropertyTypeSchema,
  required: z.boolean(),
  example: z.string().max(200).optional(),
});
export type PropertyRow = z.infer<typeof propertyRowSchema>;

export const createCategorySchema = z.object({
  categoryName: categoryNameSchema,
  displayName: displayNameSchema,
  description: descriptionSchema,
  properties: z.array(propertyRowSchema).default([]),
});
export type CreateCategoryInput = z.infer<typeof createCategorySchema>;

export const updateSchemaSchema = z.object({
  properties: z.array(propertyRowSchema),
});
export type UpdateSchemaInput = z.infer<typeof updateSchemaSchema>;
