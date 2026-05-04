import { api } from "./client";

/**
 * Sprint 1 — Application(서비스 신청서) API.
 *
 * 백엔드: com.b101.butterfly.tenant
 *  - controller/ApplicationController.java
 *  - dto/request/Application*Request.java
 *  - dto/response/ApplicationResponse.java
 */

export type ApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface ApplicationCreateRequest {
  applicantName: string;
  email: string;
  phone: string;
  birthDate: string; // YYYY-MM-DD
  businessName: string;
  businessNo: string; // 10 digits
}

export interface ApplicationRejectRequest {
  rejectReason: string;
}

export interface ApplicationResponse {
  applicationId: number;
  applicantName: string;
  email: string;
  phone: string;
  birthDate: string; // YYYY-MM-DD
  businessName: string;
  businessNo: string;
  status: ApplicationStatus;
  rejectReason: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

/**
 * Spring Data Page 응답.
 * 백엔드는 PageImpl 직렬화 결과를 그대로 내려준다 (number, totalElements, totalPages 등).
 */
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number; // 0-based current page
  size: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
}

export interface ApplicationListParams {
  status?: ApplicationStatus;
  page?: number;
  size?: number;
  sort?: string;
}

// ---------- 엔드포인트 ----------

/** 공개 신청서 제출 — 응답 data 는 생성된 applicationId(Long). */
export async function createApplication(
  req: ApplicationCreateRequest,
): Promise<number> {
  const res = await api.post<number>("/api/v1/applications", req);
  return res.data;
}

export async function listApplications(
  params: ApplicationListParams = {},
): Promise<SpringPage<ApplicationResponse>> {
  const res = await api.get<SpringPage<ApplicationResponse>>(
    "/api/v1/admin/applications",
    {
      params: {
        status: params.status,
        page: params.page ?? 0,
        size: params.size ?? 20,
        sort: params.sort ?? "createdAt,desc",
      },
    },
  );
  return res.data;
}

export async function getApplication(
  applicationId: number,
): Promise<ApplicationResponse> {
  const res = await api.get<ApplicationResponse>(
    `/api/v1/admin/applications/${applicationId}`,
  );
  return res.data;
}

export async function approveApplication(applicationId: number): Promise<void> {
  await api.post<void>(`/api/v1/admin/applications/${applicationId}/approve`);
}

export async function rejectApplication(
  applicationId: number,
  req: ApplicationRejectRequest,
): Promise<void> {
  await api.post<void>(
    `/api/v1/admin/applications/${applicationId}/reject`,
    req,
  );
}
