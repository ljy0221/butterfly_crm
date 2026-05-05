import { z } from "zod";

/**
 * Sprint 4 — Message Template Zod 스키마.
 *
 * 백엔드 검증 규칙(record + @NotBlank + Channel enum + 도메인 validate())에 맞춰 작성.
 *  - POST /api/v1/templates 의 MessageTemplateCreateRequest
 *  - 변수 자동 추출 정규식 `\{\{(\w+)\}\}` 은 백엔드와 동일 형식.
 *
 * 참고: 백엔드는 `messageTemplateName`, `channel`, `messageTemplateBody` 만 받지만
 * UI 단에서는 description / title 같은 보조 필드를 mock store에만 봉합한다.
 * 따라서 zod에서도 mock-only 필드는 .optional() 로 둔다.
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

/**
 * mock-only. 백엔드 MessageTemplate 엔티티에는 status 필드 없음.
 * Sprint 5+에서 추가 예정.
 */
export const TemplateStatusSchema = z.enum(["DRAFT", "READY", "ARCHIVED"]);
export type TemplateStatus = z.infer<typeof TemplateStatusSchema>;

// ---------- 필드 단위 스키마 ----------

export const nameSchema = z
  .string()
  .min(1, "템플릿 이름은 필수입니다")
  .max(50, "템플릿 이름은 50자 이내여야 합니다");

export const titleSchema = z
  .string()
  .max(100, "제목은 100자 이내여야 합니다")
  .optional();

export const bodySchema = z
  .string()
  .min(1, "메시지 본문은 필수입니다")
  .max(1000, "메시지 본문은 1000자 이내여야 합니다");

export const descriptionSchema = z
  .string()
  .max(200, "설명은 200자 이내여야 합니다")
  .optional();

// ---------- 폼 스키마 ----------

/**
 * 생성 폼 — 실제 POST 요청 페이로드와 mock 봉합 필드 동시 보유.
 * 실 API에는 messageTemplateName/channel/messageTemplateBody 만 전달.
 */
export const createTemplateSchema = z.object({
  messageTemplateName: nameSchema,
  channel: ChannelSchema,
  messageTemplateBody: bodySchema,
  // mock-only
  description: descriptionSchema,
  title: titleSchema,
});
export type CreateTemplateInput = z.infer<typeof createTemplateSchema>;

/**
 * 수정 폼 — 모두 mock(백엔드 PUT 미구현).
 */
export const updateTemplateSchema = z.object({
  messageTemplateName: nameSchema,
  channel: ChannelSchema,
  messageTemplateBody: bodySchema,
  description: descriptionSchema,
  title: titleSchema,
});
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
