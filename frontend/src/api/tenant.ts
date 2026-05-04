import { api } from "./client";
import type { ProjectPlan, RoleType } from "@/schemas/tenant";

/**
 * Sprint 2 — Tenant(Project/Member/API Key) API.
 *
 * 백엔드 패키지: com.b101.butterfly.tenant
 *  - controller/ProjectController.java
 *  - dto/request/UpdateMemberRoleRequest.java
 *  - dto/response/{ProjectResponse, ProjectMemberResponse, ApiKeyResponse}.java
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap 하므로
 * `res.data`가 곧 도메인 페이로드(T) 이다.
 */

// ---------- DTO 타입 (백엔드 record 필드명 그대로) ----------

export interface ProjectResponse {
  projectId: number;
  name: string;
  businessNo: string;
  companyAuthNo: string;
  ownerId: number;
  hasApiKey: boolean;
  plan: ProjectPlan;
  /** null 가능 — 백엔드 Project.dailyLimit Integer */
  dailyLimit: number | null;
}

export interface ProjectMemberResponse {
  memberId: number;
  name: string;
  email: string;
  phone: string;
  /** LocalDate — null 가능(미입력 회원) */
  birthDate: string | null;
  roleType: RoleType;
}

export interface UpdateMemberRoleRequest {
  roleType: RoleType;
}

export interface ApiKeyResponse {
  publicApiKey: string;
}

// ---------- 엔드포인트 ----------

/** 프로젝트 상세 조회. VIEWER 이상. */
export async function getProject(projectId: number): Promise<ProjectResponse> {
  const res = await api.get<ProjectResponse>(`/api/v1/projects/${projectId}`);
  return res.data;
}

/** 멤버 목록 조회. VIEWER 이상. 페이지네이션 없음(단일 List). */
export async function listMembers(
  projectId: number,
): Promise<ProjectMemberResponse[]> {
  const res = await api.get<ProjectMemberResponse[]>(
    `/api/v1/projects/${projectId}/members`,
  );
  return res.data;
}

/** 멤버 삭제(soft delete + refresh token 폐기). OWNER만. */
export async function deleteMember(
  projectId: number,
  memberId: number,
): Promise<void> {
  await api.delete<void>(
    `/api/v1/projects/${projectId}/members/${memberId}`,
  );
}

/** 멤버 권한 변경. OWNER만. */
export async function updateMemberRole(
  projectId: number,
  memberId: number,
  req: UpdateMemberRoleRequest,
): Promise<void> {
  await api.put<void>(
    `/api/v1/projects/${projectId}/members/${memberId}/role`,
    req,
  );
}

/** API 키 최초 발급. OWNER만. 평문 키는 응답에 1회만 노출. */
export async function issueApiKey(projectId: number): Promise<ApiKeyResponse> {
  const res = await api.post<ApiKeyResponse>(
    `/api/v1/projects/${projectId}/api-keys/issue`,
  );
  return res.data;
}

/** API 키 재발급(기존 키 즉시 폐기). OWNER만. */
export async function revokeApiKey(projectId: number): Promise<ApiKeyResponse> {
  const res = await api.post<ApiKeyResponse>(
    `/api/v1/projects/${projectId}/api-keys/revoke`,
  );
  return res.data;
}

/**
 * 프로젝트 삭제 (soft delete). ADMIN 전용.
 * DELETE /api/v1/admin/projects/{projectId}
 */
export async function deleteProject(projectId: number): Promise<void> {
  await api.delete<void>(`/api/v1/admin/projects/${projectId}`);
}
