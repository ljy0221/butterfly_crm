import { api } from "./client";

/**
 * Sprint 1 — Auth API.
 *
 * 백엔드 패키지: com.b101.butterfly.auth
 *  - controller/AuthController.java
 *  - dto/request/*, dto/response/*
 *
 * 응답은 client.ts의 인터셉터가 ApiResponse<T> 래퍼를 자동으로 unwrap 하므로
 * `res.data`가 곧 도메인 페이로드(T)이다.
 */

// ---------- DTO 타입 (백엔드 record 필드명 그대로) ----------

export type RoleType = "ADMIN" | "OWNER" | "EDITOR" | "VIEWER";

export interface MemberInfoResponse {
  memberId: number;
  email: string;
  name: string;
  phone: string;
  roleType: RoleType;
  projectId: number | null;
  status: "PENDING" | "ACTIVE";
  createdAt: string; // LocalDateTime
}

export interface SignupResponse {
  memberId: number;
  email: string;
  name: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  member: MemberInfoResponse;
}

export interface EmailSendCodeRequest {
  email: string;
}

export interface EmailVerifyRequest {
  email: string;
  code: string;
}

export interface SignupRequest {
  email: string;
  password: string;
  name: string;
  phone: string;
  birthDate: string; // YYYY-MM-DD
  companyAuthNo: string;
  agreedToTerms: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenRefreshRequest {
  refreshToken: string;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
}

export interface PhoneChangeRequest {
  phone: string;
}

export interface WithdrawRequest {
  password: string;
}

export interface SetOwnerPasswordRequest {
  token: string;
  password: string;
}

// ---------- 엔드포인트 ----------

export async function sendEmailCode(req: EmailSendCodeRequest): Promise<void> {
  await api.post<void>("/api/v1/auth/email/send-code", req);
}

export async function verifyEmailCode(req: EmailVerifyRequest): Promise<void> {
  await api.post<void>("/api/v1/auth/email/verify", req);
}

export async function signup(req: SignupRequest): Promise<SignupResponse> {
  const res = await api.post<SignupResponse>("/api/v1/auth/signup", req);
  return res.data;
}

export async function login(req: LoginRequest): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>("/api/v1/auth/login", req);
  return res.data;
}

export async function logout(): Promise<void> {
  await api.post<void>("/api/v1/auth/logout");
}

export async function refreshToken(
  req: TokenRefreshRequest,
): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>("/api/v1/auth/refresh", req);
  return res.data;
}

export async function getMe(): Promise<MemberInfoResponse> {
  const res = await api.get<MemberInfoResponse>("/api/v1/auth/me");
  return res.data;
}

export async function changePassword(
  req: PasswordChangeRequest,
): Promise<void> {
  await api.put<void>("/api/v1/auth/password", req);
}

export async function changePhone(req: PhoneChangeRequest): Promise<void> {
  await api.put<void>("/api/v1/auth/phone", req);
}

export async function withdraw(req: WithdrawRequest): Promise<void> {
  await api.delete<void>("/api/v1/auth/withdraw", { data: req });
}

export async function setOwnerPassword(
  req: SetOwnerPasswordRequest,
): Promise<void> {
  await api.post<void>("/api/v1/auth/owner/set-password", req);
}
