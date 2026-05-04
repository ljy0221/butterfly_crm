import { z } from "zod";

/**
 * Sprint 2 — Tenant(Project/Member/API Key) Zod 스키마.
 *
 * 백엔드 검증 규칙(record + @NotNull + RoleType enum)에 맞춰 작성.
 *  - PUT /projects/{pid}/members/{mid}/role 의 UpdateMemberRoleRequest
 *  - GET /projects/{pid} 의 ProjectResponse(검증용 — 응답 검증은 선택)
 */

// ---------- 도메인 enum ----------

/**
 * 백엔드 com.b101.butterfly.auth.constant.RoleType 와 동일.
 * 단일 enum이며 ADMIN은 백엔드 시스템 전체 관리자(프로젝트 미소속) 의미.
 */
export const RoleTypeSchema = z.enum(["ADMIN", "OWNER", "EDITOR", "VIEWER"]);
export type RoleType = z.infer<typeof RoleTypeSchema>;

/**
 * 백엔드 com.b101.butterfly.tenant.constant.ProjectPlan 와 동일.
 */
export const ProjectPlanSchema = z.enum(["FREE", "BASIC", "PRO", "ENTERPRISE"]);
export type ProjectPlan = z.infer<typeof ProjectPlanSchema>;

// ---------- 요청 스키마 ----------

/**
 * 멤버 권한 변경 — `UpdateMemberRoleRequest(roleType)`.
 * 백엔드는 enum을 그대로 받지만, FE에서는 OWNER로의 강등/이전 시도를
 * 사전 차단하지 않는다(서버가 PROJ_008로 거절 → 에러 매핑이 안내).
 * 다만 ADMIN은 프로젝트 멤버 권한이 될 수 없으므로 클라이언트에서 차단.
 */
export const UpdateRoleSchema = z.object({
  roleType: z.enum(["OWNER", "EDITOR", "VIEWER"]),
});
export type UpdateRoleInput = z.infer<typeof UpdateRoleSchema>;

// ---------- 응답 스키마(선택적 검증용) ----------

export const ProjectInfoSchema = z.object({
  projectId: z.number(),
  name: z.string(),
  businessNo: z.string(),
  companyAuthNo: z.string(),
  ownerId: z.number(),
  hasApiKey: z.boolean(),
  plan: ProjectPlanSchema,
  dailyLimit: z.number().nullable(),
});
export type ProjectInfo = z.infer<typeof ProjectInfoSchema>;

export const ProjectMemberSchema = z.object({
  memberId: z.number(),
  name: z.string(),
  email: z.string(),
  phone: z.string(),
  birthDate: z.string().nullable(),
  roleType: RoleTypeSchema,
});
export type ProjectMember = z.infer<typeof ProjectMemberSchema>;

export const ApiKeySchema = z.object({
  publicApiKey: z.string(),
});
export type ApiKey = z.infer<typeof ApiKeySchema>;
