import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as authApi from "@/api/auth";
import type {
  EmailSendCodeRequest,
  EmailVerifyRequest,
  LoginRequest,
  LoginResponse,
  MemberInfoResponse,
  PasswordChangeRequest,
  PhoneChangeRequest,
  SetOwnerPasswordRequest,
  SignupRequest,
  SignupResponse,
  WithdrawRequest,
} from "@/api/auth";

/**
 * Sprint 1 — Auth 도메인 React Query 훅.
 *
 * 캐시 키 컨벤션
 *  - ["auth", "me"]
 *  - ["auth", "email-verify-status", email]   // 발송 상태는 invalidate용
 *
 * 모든 invalidate는 ["auth"] prefix 매칭으로 일괄 처리한다.
 */

export const authKeys = {
  all: ["auth"] as const,
  me: () => [...authKeys.all, "me"] as const,
};

export function useSendCodeMutation() {
  return useMutation<void, unknown, EmailSendCodeRequest>({
    mutationFn: (req) => authApi.sendEmailCode(req),
  });
}

export function useVerifyCodeMutation() {
  return useMutation<void, unknown, EmailVerifyRequest>({
    mutationFn: (req) => authApi.verifyEmailCode(req),
  });
}

export function useSignupMutation() {
  return useMutation<SignupResponse, unknown, SignupRequest>({
    mutationFn: (req) => authApi.signup(req),
  });
}

export function useLoginMutation() {
  return useMutation<LoginResponse, unknown, LoginRequest>({
    mutationFn: (req) => authApi.login(req),
  });
}

export function useLogoutMutation() {
  const qc = useQueryClient();
  return useMutation<void, unknown, void>({
    mutationFn: () => authApi.logout(),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: authKeys.all });
    },
  });
}

export function useMyInfoQuery(enabled: boolean = true) {
  return useQuery<MemberInfoResponse>({
    queryKey: authKeys.me(),
    queryFn: () => authApi.getMe(),
    enabled,
  });
}

export function useOwnerSetPasswordMutation() {
  return useMutation<void, unknown, SetOwnerPasswordRequest>({
    mutationFn: (req) => authApi.setOwnerPassword(req),
  });
}

/**
 * 비밀번호 변경 (PUT /api/v1/auth/password).
 * 성공 시 me 캐시는 변경 사항이 없으므로 invalidate 하지 않는다.
 */
export function useChangePasswordMutation() {
  return useMutation<void, unknown, PasswordChangeRequest>({
    mutationFn: (req) => authApi.changePassword(req),
  });
}

/**
 * 전화번호 변경 (PUT /api/v1/auth/phone).
 * 성공 시 me 캐시 invalidate — 화면에 즉시 반영되도록.
 */
export function useChangePhoneMutation() {
  const qc = useQueryClient();
  return useMutation<void, unknown, PhoneChangeRequest>({
    mutationFn: (req) => authApi.changePhone(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: authKeys.me() });
    },
  });
}

/**
 * 회원 탈퇴 (DELETE /api/v1/auth/withdraw).
 * 성공 시 모든 auth 캐시 제거 — 로그아웃 흐름과 동일하게 store.clear는 호출 측에서 수행.
 */
export function useWithdrawMutation() {
  const qc = useQueryClient();
  return useMutation<void, unknown, WithdrawRequest>({
    mutationFn: (req) => authApi.withdraw(req),
    onSettled: () => {
      qc.removeQueries({ queryKey: authKeys.all });
    },
  });
}
