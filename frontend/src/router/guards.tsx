import { Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import type { ReactNode } from "react";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import type { Role } from "@/api/types";

export function RequireAuth({ children }: { children: ReactNode }) {
  // JWT 미구현 — accessToken은 빈 문자열일 수 있으므로 member만으로 인증 판정
  const isAuthed = useAuthStore((s) => !!s.member);
  const location = useLocation();
  if (!isAuthed) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <>{children}</>;
}

const ROLE_RANK: Record<Role, number> = { VIEWER: 1, EDITOR: 2, OWNER: 3 };

export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const current = useProjectStore((s) => s.current);
  if (!current) return <Navigate to="/projects" replace />;
  if (ROLE_RANK[current.role] < ROLE_RANK[role]) {
    return (
      <DenyView
        title="권한이 부족합니다"
        message={`이 작업에는 ${role} 이상의 권한이 필요합니다. (현재: ${current.role})`}
      />
    );
  }
  return <>{children}</>;
}

/**
 * URL의 :projectId가 store의 현재 프로젝트와 다르면 store를 업데이트하거나
 * 프로젝트 선택 페이지로 보낸다.
 * ADMIN은 /p/* 경로에 접근할 수 없으므로 즉시 /admin으로 리다이렉트한다.
 */
export function RequireProject({ children }: { children: ReactNode }) {
  const params = useParams<{ projectId: string }>();
  const current = useProjectStore((s) => s.current);
  const member = useAuthStore((s) => s.member);
  const urlProjectId = Number(params.projectId);

  if (!current || Number.isNaN(urlProjectId) || member?.role === "ADMIN") {
    return <Navigate to="/projects" replace />;
  }
  if (current.projectId !== urlProjectId) {
    return <Navigate to="/projects" replace />;
  }
  return <>{children}</>;
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const member = useAuthStore((s) => s.member);
  if (!member) return <Navigate to="/login" replace />;
  if (member.role !== "ADMIN") {
    return <DenyView title="관리자 전용 페이지입니다" />;
  }
  return <>{children}</>;
}

/**
 * 가드 거부 시 공통 화면. 사용자가 막다른 길에 갇히지 않도록
 * 홈/로그아웃 액션 버튼을 제공한다.
 */
function DenyView({ title, message }: { title: string; message?: string }) {
  const navigate = useNavigate();
  const member = useAuthStore((s) => s.member);
  const clear = useAuthStore((s) => s.clear);

  const goHome = () => {
    if (member?.role === "ADMIN") navigate("/admin/applications");
    else if (member?.projectId != null) navigate(`/p/${member.projectId}/dashboard`);
    else navigate("/projects");
  };

  const logout = () => {
    clear();
    navigate("/login", { replace: true });
  };

  return (
    <div
      style={{
        padding: 32,
        margin: "60px auto",
        maxWidth: 520,
        textAlign: "center",
        background: "#fff",
        border: "1px solid var(--border-default)",
        borderRadius: "var(--radius-lg)",
        boxShadow: "var(--shadow-1)",
      }}
    >
      <div style={{ fontSize: 36, marginBottom: 8 }}>🔒</div>
      <h2 style={{ marginTop: 0, marginBottom: 8, fontSize: 20 }}>{title}</h2>
      {message && (
        <p
          style={{
            color: "var(--fg-secondary)",
            margin: "0 0 24px",
            lineHeight: 1.6,
          }}
        >
          {message}
        </p>
      )}
      <div
        style={{
          display: "flex",
          gap: 8,
          justifyContent: "center",
          marginTop: message ? 0 : 16,
        }}
      >
        <button
          type="button"
          onClick={goHome}
          style={{
            padding: "8px 16px",
            background: "var(--color-primary-strong)",
            color: "#fff",
            border: 0,
            borderRadius: "var(--radius-sm)",
            cursor: "pointer",
            fontSize: 13,
            fontFamily: "inherit",
          }}
        >
          홈으로
        </button>
        <button
          type="button"
          onClick={logout}
          style={{
            padding: "8px 16px",
            background: "var(--bg-surface)",
            color: "var(--fg-primary)",
            border: "1px solid var(--border-default)",
            borderRadius: "var(--radius-sm)",
            cursor: "pointer",
            fontSize: 13,
            fontFamily: "inherit",
          }}
        >
          로그아웃
        </button>
      </div>
    </div>
  );
}
