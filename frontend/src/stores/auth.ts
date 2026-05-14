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
 */
async function refreshAccessToken(): Promise<string | null> {
  const { refreshToken, setAccessToken, clear } = useAuthStore.getState();
  if (!refreshToken) return null;
  try {
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
    const res = await fetch(`${baseUrl}/api/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      clear();
      return null;
    }
    const data = (await res.json()) as { accessToken: string; refreshToken?: string };
    setAccessToken(data.accessToken);
    if (data.refreshToken) {
      useAuthStore.setState({ refreshToken: data.refreshToken });
    }
    return data.accessToken;
  } catch {
    clear();
    return null;
  }
}

// API 클라이언트와 store 연결
registerAuthAccessor({
  getAccessToken: () => useAuthStore.getState().accessToken,
  getMemberId: () => useAuthStore.getState().member?.memberId ?? null,
  onUnauthorized: refreshAccessToken,
});
