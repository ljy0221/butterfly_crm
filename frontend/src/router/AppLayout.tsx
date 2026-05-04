import { useMemo } from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { Sidebar, Topbar } from "@/components/layout";
import { ToastHost, ModalHost } from "@/components/feedback";
import { useProjectStore } from "@/stores/project";
import { labelToPath, pathToActiveLabel } from "./nav-mapping";

/**
 * 인증 후 진입하는 메인 콘솔 레이아웃.
 * 기존 Sidebar/Topbar의 한글 라벨 어휘를 유지하면서 React Router로 변환한다.
 */
export default function AppLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const project = useProjectStore((s) => s.current);

  const handleNavigate = (label: string) => {
    if (!project) {
      navigate("/projects");
      return;
    }
    navigate(labelToPath(label, project.projectId));
  };

  const active = pathToActiveLabel(location.pathname);

  const crumbs = useMemo(() => {
    const segs = location.pathname.split("/").filter(Boolean);
    return ["Butterfly", project?.name ?? "프로젝트", ...segs.slice(2)];
  }, [location.pathname, project?.name]);

  return (
    <div
      style={{
        display: "flex",
        height: "100vh",
        background: "#f5f7fb",
        overflow: "hidden",
      }}
    >
      <Sidebar active={active} onNavigate={handleNavigate} collapsed={false} />
      <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <Topbar
          crumbs={crumbs}
          onToggleSidebar={() => {
            /* TODO: sidebar collapse state */
          }}
          onNavigate={handleNavigate}
        />
        <main
          data-screen-label={active}
          style={{ flex: 1, padding: "24px 32px 60px", overflow: "auto" }}
        >
          <Outlet />
        </main>
      </div>
      <ToastHost />
      <ModalHost />
    </div>
  );
}
