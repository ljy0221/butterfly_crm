import { create } from "zustand";
import { persist } from "zustand/middleware";
import { registerAuthAccessor } from "@/api/client";

export type AuthRole = "ADMIN" | "OWNER" | "EDITOR" | "VIEWER";

export interface AuthMember {
  memberId: number;
  email: string;
  name: string;
  /**
   * 백엔드 MemberInfoResponse.roleType 을 그대로 보관.
   * Sprint 1 라우팅: ADMIN → /admin/applications, 그 외 → /projects
   */
  role?: AuthRole;
  phone?: string;
  projectId?: number | null;
  status?: "PENDING" | "ACTIVE";
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  member: AuthMember | null;
  setAuth: (payload: {
    accessToken: string;
    refreshToken: string;
    member: AuthMember;
  }) => void;
  setAccessToken: (token: string) => void;
  setMember: (member: AuthMember) => void;
  clear: () => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      member: null,
      setAuth: ({ accessToken, refreshToken, member }) =>
        set({ accessToken, refreshToken, member }),
      setAccessToken: (token) => set({ accessToken: token }),
      setMember: (member) => set({ member }),
      clear: () => set({ accessToken: null, refreshToken: null, member: null }),
      // JWT 미구현 — 로그인 응답의 accessToken 이 빈 문자열이라
      // member 존재 여부만으로 인증 상태를 판단한다.
      isAuthenticated: () => !!get().member,
    }),
    {
      name: "butterfly-auth",
      // accessToken은 메모리 우선이지만, 새로고침 UX를 위해 sessionStorage에 보관.
      // 추후 백엔드가 httpOnly 쿠키로 refresh를 내려주면 이 부분을 비밀로 옮긴다.
      storage: {
        getItem: (key) => {
          const raw = sessionStorage.getItem(key);
          try { return raw ? JSON.parse(raw) : null; } catch { return null; }
        },
        setItem: (key, value) => sessionStorage.setItem(key, JSON.stringify(value)),
        removeItem: (key) => sessionStorage.removeItem(key),
      },
    },
  ),
);

/**
 * Refresh helper — 직접 axios 인스턴스를 사용하지 않고 fetch를 사용해
 * 401 인터셉터 무한 루프를 방지한다.
 *
 * in-flight lock: 동시에 여러 API가 401을 받아도 refresh 요청은 1번만 나가고
 * 나머지는 동일한 Promise를 기다린다.
 */
let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) return refreshPromise;
  refreshPromise = doRefresh().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

async function doRefresh(): Promise<string | null> {
  const { refreshToken, setAccessToken, clear } = useAuthStore.getState();
  if (!refreshToken) return null;
  try {
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
    const res = await fetch(`${baseUrl}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (res.status === 401) {
      // refresh token 만료·무효 → 로그아웃
      clear();
      return null;
    }
    if (!res.ok) {
      // 5xx 등 일시적 서버 오류 → 로그아웃하지 않음
      return null;
    }
    // 백엔드 응답: { success: true, data: { accessToken, refreshToken, member } }
    const envelope = (await res.json()) as {
      success: boolean;
      data: { accessToken: string; refreshToken: string; member: AuthMember };
    };
    const { accessToken: newAccess, refreshToken: newRefresh, member } = envelope.data;
    setAccessToken(newAccess);
    useAuthStore.setState({ refreshToken: newRefresh, member });
    return newAccess;
  } catch {
    // 네트워크 오류 → 로그아웃하지 않음
    return null;
  }
}

// API 클라이언트와 store 연결
registerAuthAccessor({
  getAccessToken: () => useAuthStore.getState().accessToken,
  getMemberId: () => useAuthStore.getState().member?.memberId ?? null,
  onUnauthorized: refreshAccessToken,
});
