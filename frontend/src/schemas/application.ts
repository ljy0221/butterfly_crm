import { z } from "zod";
import {
  birthDateSchema,
  emailSchema,
  nameSchema,
  phoneSchema,
} from "./auth";

/**
 * Sprint 1 — 신청서(Application) Zod 스키마.
 * 백엔드 ApplicationCreateRequest 와 1:1.
 */

export const businessNoSchema = z
  .string()
  .regex(/^\d{10}$/, "10자리 사업자번호를 입력해주세요");

export const businessNameSchema = z
  .string()
  .min(1, "사업자명을 입력하세요")
  .max(30, "30자 이내");

export const applicationSchema = z.object({
  applicantName: nameSchema,
  email: emailSchema,
  phone: phoneSchema,
  birthDate: birthDateSchema,
  businessName: businessNameSchema,
  businessNo: businessNoSchema,
  agreed: z.literal(true, {
    errorMap: () => ({ message: "개인정보 처리방침에 동의해주세요" }),
  }),
});

export type ApplicationInput = z.infer<typeof applicationSchema>;
