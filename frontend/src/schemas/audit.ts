import { z } from "zod";

/**
 * Sprint 7 — Audit 도메인 Zod 스키마.
 *
 * 백엔드 AuditLog는 action / targetType 모두 String 자유 입력이지만
 * 운영 컨벤션(ADR-0008)을 따라 enum 권고값을 둔다. 필터에서는 자유 텍스트 허용.
 */

export const AuditActionSchema = z.enum([
  "CREATE",
  "UPDATE",
  "DELETE",
  "READ",
  "EXPORT",
  "LOGIN",
  "ROLE_CHANGE",
]);
export type AuditAction = z.infer<typeof AuditActionSchema>;

export const AuditTargetTypeSchema = z.enum([
  "CAMPAIGN",
  "SEGMENT",
  "TEMPLATE",
  "MEMBER",
  "API_KEY",
  "CREDENTIAL",
  "PROJECT",
]);
export type AuditTargetType = z.infer<typeof AuditTargetTypeSchema>;

/** YYYY-MM-DD 만 허용 (백엔드 @DateTimeFormat(ISO.DATE)) */
const dateStringSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, "YYYY-MM-DD 형식")
  .optional()
  .or(z.literal(""));

export const auditDateRangeSchema = z
  .object({
    startDate: dateStringSchema,
    endDate: dateStringSchema,
  })
  .refine(
    (d) =>
      !d.startDate ||
      !d.endDate ||
      String(d.startDate) <= String(d.endDate),
    { message: "시작일은 종료일보다 이전이어야 합니다", path: ["endDate"] },
  );
export type AuditDateRangeInput = z.infer<typeof auditDateRangeSchema>;
