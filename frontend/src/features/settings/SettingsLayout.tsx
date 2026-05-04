import { useEffect, useMemo, useRef } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { PageHeader } from "@/components/common";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import { useProjectQuery } from "@/hooks/queries/tenant";
import ProjectInfoTab from "./tabs/ProjectInfoTab";
import MembersTab from "./tabs/MembersTab";
import ApiKeysTab from "./tabs/ApiKeysTab";
import AuditTab from "./tabs/AuditTab";
import ProfileTab from "./tabs/ProfileTab";

/**
 * Sprint 2 — `/p/:projectId/settings` 탭 컨테이너.
 *
 * 결정:
 *  - 탭은 search param `?tab=` (project | members | api-keys)
 *  - 새 Tabs 컴포넌트 만들지 않음 — 인라인 button[role="tab"] + 좌우/Home/End 키.
 *  - 탭 단위 RequireRole 미적용. OWNER 액션은 각 탭 내부에서 인라인 분기.
 */

const TABS = [
  { key: "profile", label: "프로필" },
  { key: "project", label: "프로젝트 정보" },
  { key: "members", label: "멤버" },
  { key: "api-keys", label: "API 키" },
  { key: "audit", label: "감사 로그" },
] as const;

type TabKey = (typeof TABS)[number]["key"];

function isTabKey(v: string | null | undefined): v is TabKey {
  return TABS.some((t) => t.key === v);
}

export default function SettingsLayout() {
  const params = useParams<{ projectId: string }>();
  const projectId = Number(params.projectId);
  const [searchParams, setSearchParams] = useSearchParams();
  const tabsRef = useRef<HTMLDivElement>(null);

  const member = useAuthStore((s) => s.member);
  const setCurrent = useProjectStore((s) => s.setCurrent);
  const current = useProjectStore((s) => s.current);

  // store가 아직 비어있거나 다른 프로젝트로 이동했을 때 동기화.
  const { data: project } = useProjectQuery(
    Number.isNaN(projectId) ? null : projectId,
  );

  useEffect(() => {
    if (!project || !member?.role || member.role === "ADMIN") return;
    if (
      !current ||
      current.projectId !== project.projectId ||
      current.name !== project.name
    ) {
      setCurrent({
        projectId: project.projectId,
        name: project.name,
        role: member.role as "OWNER" | "EDITOR" | "VIEWER",
      });
    }
  }, [project, member?.role, current, setCurrent]);

  const tabParam = searchParams.get("tab");
  const tab: TabKey = isTabKey(tabParam) ? tabParam : "project";

  const setTab = (next: TabKey) => {
    const sp = new URLSearchParams(searchParams);
    sp.set("tab", next);
    setSearchParams(sp, { replace: true });
  };

  const onTabKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const idx = TABS.findIndex((t) => t.key === tab);
    if (idx < 0) return;
    let nextIdx = idx;
    if (e.key === "ArrowRight") nextIdx = (idx + 1) % TABS.length;
    else if (e.key === "ArrowLeft")
      nextIdx = (idx - 1 + TABS.length) % TABS.length;
    else if (e.key === "Home") nextIdx = 0;
    else if (e.key === "End") nextIdx = TABS.length - 1;
    else return;
    e.preventDefault();
    setTab(TABS[nextIdx].key);
    // 다음 탭 버튼으로 포커스 이동
    const root = tabsRef.current;
    if (root) {
      const buttons = root.querySelectorAll<HTMLButtonElement>('button[role="tab"]');
      buttons[nextIdx]?.focus();
    }
  };

  const content = useMemo(() => {
    if (tab === "profile") return <ProfileTab />;
    if (Number.isNaN(projectId)) return null;
    if (tab === "project") return <ProjectInfoTab projectId={projectId} />;
    if (tab === "members") return <MembersTab projectId={projectId} />;
    if (tab === "api-keys") return <ApiKeysTab projectId={projectId} />;
    if (tab === "audit") return <AuditTab projectId={projectId} />;
    return null;
  }, [tab, projectId]);

  return (
    <div>
      <PageHeader
        title="설정"
        subtitle="프로젝트 · 멤버 · API 키를 관리합니다"
      />

      <div
        ref={tabsRef}
        role="tablist"
        aria-label="설정 탭"
        onKeyDown={onTabKeyDown}
        style={{
          display: "flex",
          gap: 4,
          borderBottom: "1px solid #e2e8f0",
          marginBottom: 20,
        }}
      >
        {TABS.map((t) => {
          const active = tab === t.key;
          return (
            <button
              key={t.key}
              role="tab"
              aria-selected={active}
              tabIndex={active ? 0 : -1}
              onClick={() => setTab(t.key)}
              style={{
                position: "relative",
                padding: "10px 14px",
                marginBottom: -1,
                background: "transparent",
                border: 0,
                borderBottom: active
                  ? "2px solid var(--color-primary-strong, #3b5bdb)"
                  : "2px solid transparent",
                color: active ? "#1f2937" : "#64748b",
                fontSize: 13,
                fontWeight: active ? 700 : 500,
                fontFamily: "inherit",
                cursor: "pointer",
              }}
            >
              {t.label}
            </button>
          );
        })}
      </div>

      <div role="tabpanel">{content}</div>
    </div>
  );
}
