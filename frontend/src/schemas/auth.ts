import { z } from "zod";

/**
 * Sprint 1 — Auth 관련 Zod 스키마.
 *
 * 백엔드 검증 규칙(예: SignupRequest의 @Pattern, @Size)과 1:1로 맞춘다.
 * 메시지는 한국어. 기본은 백엔드 처리에 맡기되, 명백한 클라 규칙은 미리 차단한다.
 */

export const emailSchema = z
  .string()
  .min(1, "이메일을 입력하세요")
  .email("올바른 이메일 형식이 아닙니다");

export const passwordSchema = z
  .string()
  .min(8, "8자 이상 입력해주세요")
  .max(20, "20자 이내")
  .regex(/[a-z]/, "영소문자를 포함해주세요")
  .regex(/[0-9]/, "숫자를 포함해주세요");

export const phoneSchema = z
  .string()
  .regex(/^\d{11}$/, "11자리 숫자(-제외)로 입력해주세요");

export const nameSchema = z
  .string()
  .min(1, "이름을 입력하세요")
  .max(20, "20자 이내");

export const companySchema = z
  .string()
  .min(1, "회사명을 입력하세요")
  .max(30, "30자 이내");

export const birthDateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, "YYYY-MM-DD 형식");

export const emailVerifyCodeSchema = z
  .string()
  .length(6, "6자리 코드를 입력해주세요");

export const companyAuthNoSchema = z
  .string()
  .min(1, "회사 인증번호를 입력하세요");

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "비밀번호를 입력하세요"),
});

export type LoginInput = z.infer<typeof loginSchema>;

/**
 * 회원가입 폼 (4-step 위저드의 합집합).
 * `company`는 백엔드 SignupRequest에 포함되지 않지만, FE step3에서 수집해 보관.
 */
export const signupSchema = z
  .object({
    email: emailSchema,
    password: passwordSchema,
    passwordConfirm: passwordSchema,
    name: nameSchema,
    phone: phoneSchema,
    birthDate: birthDateSchema,
    companyAuthNo: companyAuthNoSchema,
    agreedToTerms: z.literal(true, {
      errorMap: () => ({ message: "약관에 동의해주세요" }),
    }),
  })
  .refine((d) => d.password === d.passwordConfirm, {
    message: "비밀번호가 일치하지 않습니다",
    path: ["passwordConfirm"],
  });

export type SignupInput = z.infer<typeof signupSchema>;

// 회사명은 백엔드 SignupRequest에 포함되지 않아 위저드 step3에서 수집하지 않는다.
// (회사 인증번호 검증 시점에 백엔드가 회사를 매핑한다.)
export const personalInfoSchema = z.object({
  name: nameSchema,
  phone: phoneSchema,
  birthDate: birthDateSchema,
});

export const passwordStepSchema = z
  .object({
    password: passwordSchema,
    passwordConfirm: passwordSchema,
    agreedToTerms: z.literal(true, {
      errorMap: () => ({ message: "약관에 동의해주세요" }),
    }),
  })
  .refine((d) => d.password === d.passwordConfirm, {
    message: "비밀번호가 일치하지 않습니다",
    path: ["passwordConfirm"],
  });

export const ownerSetupSchema = z
  .object({
    password: passwordSchema,
    passwordConfirm: passwordSchema,
  })
  .refine((d) => d.password === d.passwordConfirm, {
    message: "비밀번호가 일치하지 않습니다",
    path: ["passwordConfirm"],
  });
