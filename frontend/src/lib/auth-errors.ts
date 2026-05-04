import { ApiError } from "@/api/types";

/**
 * Sprint 1 — Auth/Application 에러 코드 → UI 분기 헬퍼.
 *
 * 백엔드 enum (검증 완료):
 *  - AuthErrorCode.java   (AUTH_001 ~ AUTH_017, PROJ_001, SYS_001)
 *  - TenantErrorCode.java (PROJ_002~PROJ_008, APPLY_001~APPLY_005)
 *  - CommonErrorCode      (COMMON_001~COMMON_999)
 *
 * 결과:
 *  - field?: 어떤 폼 필드의 에러로 표시할지 (없으면 폼 레벨 배너/토스트)
 *  - message: 사용자에게 노출할 한국어 메시지
 *  - jumpStep?: 회원가입 위저드에서 어느 step 으로 점프할지 (1~4)
 */
export interface AuthErrorMapping {
  field?: string;
  message: string;
  jumpStep?: 1 | 2 | 3 | 4;
}

export interface MapAuthErrorOptions {
  /**
   * 코드별 필드 오버라이드. 이를테면 페이지마다 같은 AUTH_006을
   * "email" 또는 "password" 어디에 붙일지 다르게 둘 수 있다.
   */
  fields?: Record<string, string>;
}

const DEFAULT_FIELDS: Record<string, string> = {
  // ── Auth ─────────────────────────────────────────────
  AUTH_001: "email",          // 이미 사용 중인 이메일
  AUTH_002: "email",          // 이메일 인증 필요
  AUTH_003: "code",           // 인증 코드 만료
  AUTH_004: "code",           // 인증 코드 불일치
  AUTH_005: "email",          // 인증 코드 발송 횟수 초과
  AUTH_006: "password",       // 자격증명 오류 → 보통 password 인풋에 표시
  AUTH_009: "currentPassword",
  AUTH_010: "newPassword",
  AUTH_015: "companyAuthNo",  // 회사 인증번호 유효하지 않음
  // ── Application ──────────────────────────────────────
  APPLY_001: "businessNo",    // 형식 오류
  APPLY_002: "businessNo",    // 중복 사업자번호
  APPLY_005: "rejectReason",  // 반려 사유 필수
};

const DEFAULT_MESSAGES: Record<string, string> = {
  // Auth
  AUTH_001: "이미 사용 중인 이메일입니다",
  AUTH_002: "이메일 인증이 필요합니다",
  AUTH_003: "인증 코드가 만료되었습니다. 다시 발송해주세요",
  AUTH_004: "인증 코드가 올바르지 않습니다",
  AUTH_005: "인증 코드 발송 횟수를 초과했습니다. 잠시 후 다시 시도해주세요",
  AUTH_006: "이메일 또는 비밀번호가 올바르지 않습니다",
  AUTH_008: "존재하지 않는 회원입니다",
  AUTH_009: "현재 비밀번호가 올바르지 않습니다",
  AUTH_010: "현재 비밀번호와 동일합니다",
  AUTH_011: "유효하지 않은 토큰입니다",
  AUTH_012: "유효하지 않은 리프레시 토큰입니다",
  AUTH_013: "리프레시 토큰이 만료되었습니다",
  AUTH_014: "접근 권한이 없습니다",
  AUTH_015: "유효하지 않은 회사 인증번호입니다",
  AUTH_016: "신청서 승인 후 받은 이메일에서 비밀번호를 먼저 설정해주세요",
  AUTH_017: "인증 링크가 만료되었습니다",
  PROJ_001: "프로젝트 소유자는 탈퇴할 수 없습니다",
  SYS_001: "암호화 처리 중 오류가 발생했습니다",
  // Application
  APPLY_001: "사업자 번호 형식이 올바르지 않습니다",
  APPLY_002: "이미 등록된 사업자 번호입니다",
  APPLY_003: "이미 처리된 신청서입니다",
  APPLY_004: "존재하지 않는 신청서입니다",
  APPLY_005: "반려 사유를 입력해야 합니다",
  // Common
  COMMON_001: "요청이 올바르지 않습니다",
  COMMON_002: "인증이 필요합니다",
  COMMON_003: "접근 권한이 없습니다",
  COMMON_004: "리소스를 찾을 수 없습니다",
  COMMON_005: "리소스 상태가 충돌합니다",
  COMMON_999: "서버 내부 오류가 발생했습니다",
};

/**
 * Signup 위저드 step jump 매핑.
 *  step1 = EmailVerify, step2 = CompanyAuth, step3 = PersonalInfo, step4 = Password
 */
const SIGNUP_JUMP: Record<string, 1 | 2 | 3 | 4> = {
  AUTH_001: 1,
  AUTH_002: 1,
  AUTH_003: 1,
  AUTH_004: 1,
  AUTH_005: 1,
  AUTH_015: 2,
};

export function mapAuthError(
  err: unknown,
  options: MapAuthErrorOptions = {},
): AuthErrorMapping {
  const fields = { ...DEFAULT_FIELDS, ...(options.fields ?? {}) };

  if (err instanceof ApiError) {
    const code = err.code ?? "";
    const message =
      DEFAULT_MESSAGES[code] ??
      err.message ??
      "요청 처리 중 오류가 발생했습니다";
    return {
      field: fields[code],
      message,
      jumpStep: SIGNUP_JUMP[code],
    };
  }

  // Non-ApiError (network / timeout etc.)
  const message =
    err instanceof Error ? err.message : "일시적인 오류가 발생했습니다";
  return { message };
}

/**
 * 위저드용 보조 함수: 에러를 받아 step jump 가 필요하면 그 step 을, 아니면 null.
 */
export function getSignupJumpStep(err: unknown): 1 | 2 | 3 | 4 | null {
  if (err instanceof ApiError && err.code) {
    return SIGNUP_JUMP[err.code] ?? null;
  }
  return null;
}
