import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";
import { ApiError, type ApiErrorBody, type ApiResponseEnvelope } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

/**
 * 토큰 / 멤버 컨텍스트 액세서.
 * 순환 import를 피하기 위해 store가 직접 등록한다.
 *
 * 백엔드 인증 매개는 환경별로 다르다:
 * - 로컬/개발 (`!prod` 프로파일): `LocalAuthFilter` 가 `X-Member-Id` 헤더로 SecurityContext 를 채움
 * - 배포 (`prod`): `JwtAuthenticationFilter` 가 `Authorization` Bearer 토큰으로 SecurityContext 를 채움
 *
 * 프론트는 빌드 모드로 분기 — DEV 빌드에서만 X-Member-Id 송신, PROD 빌드는 Authorization 만 송신.
 * 배포 빌드가 위조 가능한 헤더를 만들지 않도록 보안 매개를 명확히 분리.
 */
type TokenAccessor = {
  getAccessToken: () => string | null;
  getMemberId: () => number | null;
  onUnauthorized: () => Promise<string | null>; // refresh attempt; resolves to new access token or null
};

let accessor: TokenAccessor | null = null;

export function registerAuthAccessor(a: TokenAccessor): void {
  accessor = a;
}

export const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { "Content-Type": "application/json" },
});

// ---------- Request: attach Authorization (+ X-Member-Id in DEV only) ----------
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = accessor?.getAccessToken() ?? null;
  // 백엔드 JWT가 아직 미구현이라 빈 문자열을 내려줄 수 있음 → 빈 토큰은 헤더 첨부 X
  if (token && token.length > 0) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  // DEV 빌드에서만 X-Member-Id 송신 — LocalAuthFilter(@Profile("!prod")) 호환용.
  // PROD 빌드는 JwtAuthenticationFilter 가 Authorization 만 사용하므로 헤더 송신 안 함.
  if (import.meta.env.DEV) {
    const memberId = accessor?.getMemberId() ?? null;
    if (memberId !== null) {
      config.headers.set("X-Member-Id", String(memberId));
    }
  }
  return config;
});

// ---------- Response: ApiResponse<T> 자동 unwrap + 401 refresh ----------
type RetriableConfig = AxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(
  (res) => {
    // ApiResponse<T> 래퍼 자동 unwrap.
    // 백엔드 모든 엔드포인트가 { success, data, error } 형식으로 응답하므로
    // 호출 측은 res.data로 곧바로 도메인 페이로드(T)를 받게 된다.
    const body = res.data as ApiResponseEnvelope<unknown> | unknown;
    if (
      body &&
      typeof body === "object" &&
      "success" in (body as object) &&
      "data" in (body as object)
    ) {
      const env = body as ApiResponseEnvelope<unknown>;
      if (env.success === false) {
        throw new ApiError(
          env.error?.message ?? "요청이 실패했습니다.",
          res.status,
          env.error,
        );
      }
      // success=true 면 data만 그대로 노출
      res.data = env.data;
    }
    return res;
  },
  async (error: AxiosError<ApiResponseEnvelope<unknown>>) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status ?? 0;

    if (status === 401 && original && !original._retried && accessor) {
      original._retried = true;
      const newToken = await accessor.onUnauthorized();
      if (newToken) {
        original.headers = {
          ...(original.headers ?? {}),
          Authorization: `Bearer ${newToken}`,
        };
        return api.request(original);
      }
    }

    const env = error.response?.data;
    const errBody: ApiErrorBody | null =
      env && typeof env === "object" && "error" in env
        ? (env as ApiResponseEnvelope<unknown>).error
        : null;
    const message =
      errBody?.message ?? error.message ?? "요청 처리 중 오류가 발생했습니다.";
    throw new ApiError(message, status, errBody);
  },
);

export { BASE_URL };
