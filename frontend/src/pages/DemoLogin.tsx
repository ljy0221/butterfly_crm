import { useNavigate } from "react-router-dom";
import { useAuthStore, type AuthRole } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";

/**
 * 개발 전용 데모 진입 페이지 (`/demo`).
 *
 * 백엔드 없이 UI 전체를 둘러보기 위한 우회 경로.
 * 4개 role 카드 클릭 시 sessionStorage(auth + project store)에 가짜 세션을 주입하고
 * 적절한 라우트로 리다이렉트한다.
 *
 * - JWT 미구현 상태이므로 accessToken은 빈 문자열로 둬도 store/guards/client.ts가 모두 동작.
 * - X-Member-Id 헤더는 axios 인터셉터가 자동 첨부 — 실제 백엔드가 떠 있으면 해당 ID로 호출됨.
 * - import.meta.env.DEV 일 때만 router에서 마운트되므로 production 빌드엔 노출 X.
 */

interface DemoRole {
  key: string;
  label: string;
  description: string;
  role: AuthRole;
  memberId: number;
  email: string;
  name: string;
  projectId: number | null;
  destination: string;
  accent: string;
}

const PRESETS: DemoRole[] = [
  {
    key: "owner",
    label: "OWNER",
    description:
      "프로젝트 최상위 권한. API 키 발급, 멤버 권한 수정, FCM 키 등록까지 모두 가능.",
    role: "OWNER",
    memberId: 101,
    email: "owner@butterfly.io",
    name: "데모 오너",
    projectId: 1,
    destination: "/p/1/dashboard",
    accent: "#3b5bdb",
  },
  {
    key: "editor",
    label: "EDITOR",
    description:
      "세그먼트·캠페인·템플릿 CRUD 가능. 멤버 권한 수정·API 키 발급은 비활성.",
    role: "EDITOR",
    memberId: 102,
    email: "editor@butterfly.io",
    name: "데모 에디터",
    projectId: 1,
    destination: "/p/1/dashboard",
    accent: "#16a34a",
  },
  {
    key: "viewer",
    label: "VIEWER",
    description:
      "임시 권한(가입 기본값). 모든 액션 비활성, 조회만 가능. 권한 분기 UI 검증용.",
    role: "VIEWER",
    memberId: 103,
    email: "viewer@butterfly.io",
    name: "데모 뷰어",
    projectId: 1,
    destination: "/p/1/dashboard",
    accent: "#64748b",
  },
  {
    key: "admin",
    label: "ADMIN",
    description:
      "Butterfly 운영진. 신청서 승인/반려, 모든 프로젝트 감사 로그 가로 조회.",
    role: "ADMIN",
    memberId: 1,
    email: "admin@butterfly.io",
    name: "데모 관리자",
    projectId: null,
    destination: "/admin/applications",
    accent: "#7c3aed",
  },
];

export default function DemoLogin() {
  const navigate = useNavigate();
  const setAuth = useAuthStore((s) => s.setAuth);
  const setProject = useProjectStore((s) => s.setCurrent);
  const clearAuth = useAuthStore((s) => s.clear);
  const clearProject = useProjectStore((s) => s.clear);

  const enter = (preset: DemoRole) => {
    setAuth({
      accessToken: "",
      refreshToken: "",
      member: {
        memberId: preset.memberId,
        email: preset.email,
        name: preset.name,
        role: preset.role,
        projectId: preset.projectId,
      },
    });

    if (preset.role !== "ADMIN" && preset.projectId != null) {
      setProject({
        projectId: preset.projectId,
        name: "데모 프로젝트",
        role: preset.role as "OWNER" | "EDITOR" | "VIEWER",
      });
    } else {
      clearProject();
    }

    navigate(preset.destination, { replace: true });
  };

  const reset = () => {
    clearAuth();
    clearProject();
  };

  return (
    <div
      style={{
        width: "100%",
        maxWidth: 880,
        margin: "0 auto",
        padding: 24,
      }}
    >
      <div style={{ marginBottom: 24, textAlign: "center" }}>
        <div
          style={{
            display: "inline-block",
            padding: "4px 10px",
            background: "rgba(245,158,11,0.15)",
            color: "#b45309",
            fontSize: 11,
            fontWeight: 700,
            borderRadius: 999,
            letterSpacing: "0.04em",
            marginBottom: 12,
          }}
        >
          DEV ONLY
        </div>
        <h1
          style={{
            fontSize: 24,
            margin: 0,
            color: "var(--fg-strong)",
            fontFamily: "var(--font-display)",
          }}
        >
          데모 세션 진입
        </h1>
        <p
          style={{
            color: "var(--fg-secondary)",
            fontSize: 13,
            margin: "8px 0 0",
            lineHeight: 1.6,
          }}
        >
          백엔드 없이 UI를 둘러보기 위한 가짜 세션입니다. 카드를 클릭하면 해당 role의
          세션이 sessionStorage에 주입되고 자동으로 이동합니다.
          <br />
          실 API를 호출하는 페이지(대시보드/세그먼트/감사 등)는 에러 카드를 표시하고,
          mock-fallback 페이지(템플릿/캠페인/이벤트)는 정상 동작합니다.
        </p>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(2, 1fr)",
          gap: 14,
        }}
      >
        {PRESETS.map((p) => (
          <button
            key={p.key}
            type="button"
            onClick={() => enter(p)}
            style={{
              textAlign: "left",
              padding: 20,
              background: "#fff",
              border: "1px solid var(--border-default)",
              borderLeft: `4px solid ${p.accent}`,
              borderRadius: "var(--radius-lg)",
              cursor: "pointer",
              fontFamily: "inherit",
              boxShadow: "var(--shadow-1)",
              transition: "transform 120ms, box-shadow 120ms",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.transform = "translateY(-2px)";
              e.currentTarget.style.boxShadow = "var(--shadow-2)";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.transform = "";
              e.currentTarget.style.boxShadow = "var(--shadow-1)";
            }}
          >
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                marginBottom: 8,
              }}
            >
              <span
                style={{
                  fontFamily: "var(--font-display)",
                  fontSize: 16,
                  fontWeight: 700,
                  color: p.accent,
                  letterSpacing: "0.04em",
                }}
              >
                {p.label}
              </span>
              <span
                style={{
                  fontSize: 11,
                  color: "var(--fg-faint)",
                  fontFamily: "var(--font-mono)",
                }}
              >
                memberId={p.memberId}
              </span>
            </div>
            <div
              style={{
                fontSize: 13,
                color: "var(--fg-strong)",
                fontWeight: 500,
                marginBottom: 6,
              }}
            >
              {p.name}
              <span style={{ color: "var(--fg-secondary)", fontWeight: 400 }}>
                {" · "}
                {p.email}
              </span>
            </div>
            <div
              style={{
                fontSize: 12,
                color: "var(--fg-secondary)",
                lineHeight: 1.6,
                marginBottom: 12,
              }}
            >
              {p.description}
            </div>
            <div
              style={{
                fontSize: 11,
                color: p.accent,
                fontFamily: "var(--font-mono)",
              }}
            >
              → {p.destination}
            </div>
          </button>
        ))}
      </div>

      <div
        style={{
          marginTop: 20,
          padding: 14,
          background: "var(--bg-subtle)",
          border: "1px dashed var(--border-default)",
          borderRadius: "var(--radius-md)",
          fontSize: 12,
          color: "var(--fg-secondary)",
          lineHeight: 1.7,
        }}
      >
        <strong style={{ color: "var(--fg-strong)" }}>가장 완성도 높게 보이는 페이지</strong>
        <br />
        <code style={{ fontFamily: "var(--font-mono)", fontSize: 11 }}>
          /p/1/templates
        </code>{" "}
        (Sprint 4 mock 6건),{" "}
        <code style={{ fontFamily: "var(--font-mono)", fontSize: 11 }}>
          /p/1/campaigns
        </code>{" "}
        (Sprint 6 mock 8건 + 위저드),{" "}
        <code style={{ fontFamily: "var(--font-mono)", fontSize: 11 }}>
          /p/1/segments/new
        </code>{" "}
        (트리 빌더, mock 미리보기) — 이 셋은 백엔드 없이도 완전 동작합니다.
      </div>

      <div style={{ marginTop: 20, textAlign: "center" }}>
        <button
          type="button"
          onClick={reset}
          style={{
            padding: "8px 14px",
            background: "transparent",
            border: "1px solid var(--border-default)",
            borderRadius: "var(--radius-sm)",
            color: "var(--fg-secondary)",
            fontSize: 12,
            cursor: "pointer",
            fontFamily: "inherit",
          }}
        >
          세션 초기화
        </button>
      </div>
    </div>
  );
}
