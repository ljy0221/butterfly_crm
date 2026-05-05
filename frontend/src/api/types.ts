/**
 * 백엔드 공통 응답/페이지네이션 타입.
 * 백엔드의 ApiResponse<T> 래퍼와 GlobalExceptionHandler 응답 구조를 따른다.
 */

export interface ApiResponseEnvelope<T> {
  success: boolean;
  data: T | null;
  error: ApiErrorBody | null;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  // 일부 도메인은 fieldErrors를 추가로 내려줄 수 있음
  fieldErrors?: Array<{ field: string; message: string }>;
}

/**
 * 백엔드 공통 PageResponse 와이어 포맷 (butterfly-common/global/response/PageResponse.java).
 *
 * <pre>
 * { content: T[], page: int, size: int, totalElements: long, totalPages: int }
 * </pre>
 *
 * 주의: Spring Data PageImpl 의 직렬화 필드명(`number`)와는 다르다 — butterfly-common 은 record 로
 * 직접 정의된 포맷을 사용한다. 일부 도메인(segment 등)이 자체 타입으로 같은 형태를 표현해 왔으나
 * Sprint 7부터는 본 공용 타입으로 통일.
 */
export interface PageResponse<T> {
  content: T[];
  page: number; // 0-based current page index
  size: number;
  totalElements: number;
  totalPages: number;
  first?: boolean;
  last?: boolean;
  empty?: boolean;
}

export class ApiError extends Error {
  status: number;
  code?: string;
  fieldErrors?: Array<{ field: string; message: string }>;

  constructor(message: string, status: number, body?: ApiErrorBody | null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = body?.code;
    this.fieldErrors = body?.fieldErrors;
  }
}

export type Role = "VIEWER" | "EDITOR" | "OWNER";
export type AdminRole = "ADMIN";
