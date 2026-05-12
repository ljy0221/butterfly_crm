import { z } from "zod";

/**
 * Sprint 4 — Message Template Zod 스키마.
 *
 * 백엔드 검증 규칙(record + @NotBlank + Channel enum + 도메인 validate())에 맞춰 작성.
 *  - POST /api/v1/projects/{projectId}/templates 의 MessageTemplateCreateRequest
 *  - 변수 자동 추출 정규식 `\{\{(\w+)\}\}` 은 백엔드와 동일 형식.
 *
 * S14P31B101-233 이후 description / title / imageUrl 모두 백엔드 본 필드.
 * description: @NotBlank max 500, title: @NotBlank max 255, imageUrl: optional max 500.
 */

// ---------- 도메인 enum ----------

/**
 * 백엔드 com.b101.butterfly.campaign.campaign.domain.enums.Channel 와 동일.
 */
export const ChannelSchema = z.enum([
  "PUSH",
  "EMAIL",
  "SMS",
  "WEBHOOK",
  "KAKAO",
]);
export type Channel = z.infer<typeof ChannelSchema>;

// ---------- 필드 단위 스키마 ----------

export const nameSchema = z
  .string()
  .min(1, "템플릿 이름은 필수입니다")
  .max(255, "템플릿 이름은 255자 이내여야 합니다");

export const titleSchema = z
  .string()
  .min(1, "제목은 필수입니다")
  .max(255, "제목은 255자 이내여야 합니다");

export const bodySchema = z
  .string()
  .min(1, "메시지 본문은 필수입니다")
  .max(10000, "메시지 본문은 10000자 이내여야 합니다");

export const descriptionSchema = z
  .string()
  .min(1, "설명은 필수입니다")
  .max(500, "설명은 500자 이내여야 합니다");

export const imageUrlSchema = z
  .string()
  .max(500, "이미지 URL은 500자 이내여야 합니다")
  .optional();

// ---------- 폼 스키마 ----------

/**
 * 생성/수정 폼 — 백엔드 MessageTemplateCreateRequest record 와 동일 필드.
 */
export const createTemplateSchema = z.object({
  messageTemplateName: nameSchema,
  messageTemplateDescription: descriptionSchema,
  channel: ChannelSchema,
  messageTemplateTitle: titleSchema,
  messageTemplateBody: bodySchema,
  messageTemplateImageUrl: imageUrlSchema,
});
export type CreateTemplateInput = z.infer<typeof createTemplateSchema>;

export const updateTemplateSchema = createTemplateSchema;
export type UpdateTemplateInput = z.infer<typeof updateTemplateSchema>;

// ---------- 헬퍼 ----------

/**
 * 메시지 본문에서 `{{variable}}` 패턴 추출.
 * 백엔드 `MessageTemplateVariableExtractor` 와 동일 정규식.
 * 중복 변수는 한 번만 반환(순서 유지).
 */
export function extractVariables(body: string): string[] {
  const re = /\{\{(\w+)\}\}/g;
  const seen = new Set<string>();
  const result: string[] = [];
  for (const match of body.matchAll(re)) {
    const name = match[1];
    if (!seen.has(name)) {
      seen.add(name);
      result.push(name);
    }
  }
  return result;
}
