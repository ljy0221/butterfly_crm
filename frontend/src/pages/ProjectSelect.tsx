import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import { useProjectQuery } from "@/hooks/queries/tenant";
import { ApiError } from "@/api/types";
import { Button } from "@/components/common";

/**
 * Sprint 2 — 프로젝트 선택 / fallback 환영 화면.
 *
 * 동작:
 *  1) `useAuthStore.member` 확인. 없으면 RequireAuth가 이미 /login 으로 보냄.
 *  2) ADMIN(projectId 없음) → 안내 카드 + [관리자 페이지로]
 *  3) member.projectId 존재 → useProjectQuery 후 store.setCurrent → /p/{id}/dashboard 자동 이동.
 *  4) 403/404 등 에러 → 권한 안내 + [로그아웃]
 */
export default function ProjectSelect() {
  const navigate = useNavigate();
  const member = useAuthStore((s) => s.member);
  const clearAuth = useAuthStore((s) => s.clear);
  const setCurrent = useProjectStore((s) => s.setCurrent);
  const clearProject = useProjectStore((s) => s.clear);
  const current = useProjectStore((s) => s.current);

  const projectId = member?.projectId ?? null;
  const role = member?.role;

  const { data: project, isLoading, isError, error } = useProjectQuery(
    projectId,
    projectId != null,
  );

  // 프로젝트 조회 성공 시 store 갱신 + dashboard로 즉시 이동.
  // 동일 프로젝트 재진입 시 setCurrent를 무조건 호출하면 sidebar 등 zustand 구독자가
  // 불필요하게 재렌더되므로 가드한다.
  useEffect(() => {
    if (!project || !role || role === "ADMIN") return;
    const narrowed = role as "OWNER" | "EDITOR" | "VIEWER";
    if (
      !current ||
      current.projectId !== project.projectId ||
      current.role !== narrowed ||
      current.name !== project.name
    ) {
      setCurrent({
        projectId: project.projectId,
        name: project.name,
        role: narrowed,
      });
    }
    navigate(`/p/${project.projectId}/dashboard`, { replace: true });
  }, [project, role, current, setCurrent, navigate]);

  const onLogout = () => {
    clearAuth();
    navigate("/login", { replace: true });
  };

  // ── 승인 대기 중인 멤버
  if (member && member.status === "PENDING") {
    return (
      <Shell>
        <Welcome name={member.name} />
        <Card>
          <div style={{ textAlign: "center" }}>
            <h3 style={{ ...cardTitleStyle, textAlign: "center" }}>가입 승인 대기 중입니다</h3>
            <div style={{
              margin: "12px auto 0",
              padding: "10px 14px",
              background: "#f8fafc",
              border: "1px solid #e2e8f0",
              borderRadius: 8,
              fontSize: 12,
              color: "#64748b",
              lineHeight: 1.7,
              textAlign: "left",
            }}>
              <div style={{ fontWeight: 600, color: "#475569", marginBottom: 4 }}>안내</div>
              프로젝트 관리자가 요청을 검토 후 승인합니다.
              <br />
              승인이 완료되면 가입 시 등록한 이메일로 알림이 발송됩니다.
            </div>
            <div style={{ marginTop: 20 }}>
              <Button variant="ghost" size="md" onClick={onLogout}>
                로그아웃
              </Button>
            </div>
          </div>
        </Card>
      </Shell>
    );
  }

  // ── ADMIN(소속 프로젝트 없음) 안내
  if (member && (member.role === "ADMIN" || member.projectId == null)) {
    return (
      <Shell>
        <Welcome name={member.name} />
        <Card>
          <h3 style={cardTitleStyle}>소속 프로젝트가 없습니다</h3>
          <p style={cardDescStyle}>
            관리자 계정은 별도의 프로젝트에 소속되지 않습니다.
            관리자 페이지에서 신청서를 검토하거나, 로그아웃 후 다른 계정으로 로그인하세요.
          </p>
          <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
            <Button
              variant="primaryStrong"
              size="md"
              onClick={() => { clearProject(); navigate("/admin/applications"); }}
            >
              관리자 페이지로
            </Button>
            <Button variant="ghost" size="md" onClick={onLogout}>
              로그아웃
            </Button>
          </div>
        </Card>
      </Shell>
    );
  }

  // ── 권한 없음 / 프로젝트 없음 (403, 404)
  if (isError) {
    const apiErr = error instanceof ApiError ? error : null;
    const status = apiErr?.status ?? 0;
    const title =
      status === 403 ? "프로젝트 접근 권한이 없습니다" : "프로젝트를 찾을 수 없습니다";
    return (
      <Shell>
        <Welcome name={member?.name ?? ""} />
        <Card>
          <h3 style={cardTitleStyle}>{title}</h3>
          <p style={cardDescStyle}>
            {apiErr?.message ?? "프로젝트 정보를 불러올 수 없습니다."}
          </p>
          <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
            <Button variant="ghost" size="md" onClick={onLogout}>
              로그아웃
            </Button>
          </div>
        </Card>
      </Shell>
    );
  }

  // ── 로딩 / 정상(곧 redirect) — 짧은 fallback 환영 화면
  return (
    <Shell>
      <Welcome name={member?.name ?? ""} />
      <Card>
        {isLoading || !project ? (
          <SkeletonProject />
        ) : (
          <>
            <h3 style={cardTitleStyle}>{project.name}</h3>
            <div style={{ color: "var(--fg-secondary)", fontSize: 13 }}>
              {role} · {project.plan} 플랜
            </div>
            <div style={{ marginTop: 16 }}>
              <Button
                variant="primaryStrong"
                size="md"
                onClick={() =>
                  navigate(`/p/${project.projectId}/dashboard`, { replace: true })
                }
              >
                대시보드로 이동
              </Button>
            </div>
          </>
        )}
        <div
          style={{
            marginTop: 24,
            paddingTop: 16,
            borderTop: "1px solid #f1f5f9",
            fontSize: 12,
            color: "#94a3b8",
            display: "flex",
            justifyContent: "center",
            gap: 6,
          }}
        >
          다른 계정으로
          <button
            type="button"
            onClick={onLogout}
            style={{
              border: 0,
              background: "transparent",
              color: "#3b5bdb",
              fontSize: 12,
              fontFamily: "inherit",
              cursor: "pointer",
              padding: 0,
            }}
          >
            로그아웃
          </button>
        </div>
      </Card>
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        background: "linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%)",
      }}
    >
      <div style={{ width: "100%", maxWidth: 420 }}>{children}</div>
    </div>
  );
}

function Welcome({ name }: { name: string }) {
  return (
    <div style={{ textAlign: "center", marginBottom: 24 }}>
      <div
        style={{
          width: 44,
          height: 44,
          borderRadius: 12,
          margin: "0 auto 12px",
          background: "linear-gradient(135deg, #3b5bdb, #637fff)",
          color: "#fff",
          fontSize: 22,
          fontWeight: 800,
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          letterSpacing: "-0.02em",
          fontFamily: "var(--font-display)",
        }}
      >
        B
      </div>
      <div
        style={{
          fontSize: 20,
          fontWeight: 700,
          color: "#1f2937",
          fontFamily: "var(--font-display)",
        }}
      >
        Butterfly
      </div>
      {name && (
        <div style={{ marginTop: 4, fontSize: 13, color: "#64748b" }}>
          반갑습니다, {name} 님
        </div>
      )}
    </div>
  );
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 14,
        padding: 24,
        boxShadow: "0 8px 24px -8px rgba(15,23,42,.08)",
      }}
    >
      {children}
    </div>
  );
}

const cardTitleStyle: React.CSSProperties = {
  margin: 0,
  fontSize: 16,
  fontWeight: 700,
  color: "#1f2937",
  fontFamily: "var(--font-display)",
  marginBottom: 6,
};

const cardDescStyle: React.CSSProperties = {
  margin: 0,
  fontSize: 13,
  color: "#64748b",
  lineHeight: 1.6,
};

function SkeletonProject() {
  return (
    <div>
      <div
        style={{
          width: "60%",
          height: 18,
          borderRadius: 6,
          background: "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
          backgroundSize: "200% 100%",
          animation: "shimmer 1.4s infinite",
          marginBottom: 8,
        }}
      />
      <div
        style={{
          width: "40%",
          height: 14,
          borderRadius: 6,
          background: "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
          backgroundSize: "200% 100%",
          animation: "shimmer 1.4s infinite",
        }}
      />
      <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>
    </div>
  );
}
