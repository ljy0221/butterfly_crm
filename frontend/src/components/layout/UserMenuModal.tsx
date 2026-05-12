import { Icon } from "../common/Icon.jsx";
import Avatar from "../common/Avatar.jsx";
import Tag from "../common/Tag.jsx";
import { confirmDialog, toast } from "../../lib/ui-bus";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import * as authApi from "@/api/auth";

/**
 * Topbar 우측 사용자 메뉴 모달.
 *
 * Sprint 1 mock(.jsx)에서는 CRM_DATA.USER 더미를 보여줬지만,
 * 이제 실 useAuthStore와 useLogoutMutation을 연결해 다음을 수행한다:
 *  - 회원 정보 표시 (store에 보관된 member)
 *  - "프로필 설정" → 현재 프로젝트 settings?tab=profile 로 이동
 *  - "로그아웃" → confirmDialog → POST /api/v1/auth/logout → store.clear → /login
 *  - "API 키 관리" → settings?tab=api-keys (OWNER만 가능, 비-OWNER는 안내만)
 */

interface Props {
  close: () => void;
  onNavigate: (route: string) => void;
}

interface MenuItem {
  label?: string;
  icon?: React.ReactNode;
  onClick?: () => void;
  danger?: boolean;
  divider?: boolean;
}

export default function UserMenuModal({ close, onNavigate }: Props) {
  const member = useAuthStore((s) => s.member);
  const project = useProjectStore((s) => s.current);
  const clearAuth = useAuthStore((s) => s.clear);
  const clearProject = useProjectStore((s) => s.clear);

  if (!member) {
    close();
    return null;
  }

  const goSettings = (tab: "profile" | "api-keys") => {
    close();
    if (project == null) {
      toast("프로젝트를 먼저 선택해주세요", "info");
      return;
    }
    window.location.assign(`/p/${project.projectId}/settings?tab=${tab}`);
  };

  const onLogout = async () => {
    // close()를 미리 호출하면 컴포넌트가 unmount되어 mutation/콜백이 사라진다.
    // → confirmDialog는 별도 모달이라 메뉴 모달이 떠 있어도 위에 띄워진다.
    const ok = await confirmDialog({
      title: "로그아웃하시겠습니까?",
      message: "현재 세션이 종료됩니다.",
      confirmText: "로그아웃",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) {
      close();
      return;
    }

    // 백엔드 호출은 best-effort — 토큰이 빈 문자열이거나 백엔드가 다운돼도
    // 클라이언트 측 세션은 항상 정리하고 로그인으로 이동한다.
    // mutation hook을 쓰면 close 시 hook이 unmount돼 콜백 누락되므로 직접 호출.
    try {
      await authApi.logout();
    } catch {
      // 401/403/Network Error 모두 무시
    }
    clearAuth();
    clearProject();
    toast("로그아웃되었습니다", "info");
    close();
    window.location.assign("/login");
  };

  const isAdmin = member.role === "ADMIN";

  const items: MenuItem[] = [
    ...(!isAdmin
      ? [
          {
            label: "프로필 설정",
            icon: Icon.edit(13),
            onClick: () => goSettings("profile"),
          },
          {
            label: "API 키 관리",
            icon: Icon.copy(13),
            onClick: () => goSettings("api-keys"),
          },
          {
            label: "도움말 센터",
            icon: Icon.search(13),
            onClick: () => {
              close();
              toast("도움말 센터를 준비 중입니다", "info");
            },
          },
          { divider: true },
        ]
      : []),
    {
      label: "로그아웃",
      icon: Icon.back(13),
      danger: true,
      onClick: onLogout,
    },
  ];

  return (
    <div
      style={{
        position: "fixed",
        top: 60,
        right: 24,
        width: 280,
        background: "#fff",
        borderRadius: 12,
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
      }}
    >
      <div
        style={{
          padding: "16px 18px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          alignItems: "center",
          gap: 10,
        }}
      >
        <Avatar name={member.name} size={36} style={{}} />
        <div style={{ minWidth: 0, flex: 1 }}>
          <div
            style={{
              fontSize: 13,
              fontWeight: 700,
              color: "#1f2937",
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
          >
            {member.name}
          </div>
          <div
            style={{
              fontSize: 11,
              color: "#64748b",
              whiteSpace: "nowrap",
              overflow: "hidden",
              textOverflow: "ellipsis",
            }}
          >
            {member.email}
          </div>
        </div>
      </div>

      <div
        style={{
          padding: "10px 14px",
          background: "#fafbfe",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          alignItems: "center",
          gap: 8,
        }}
      >
        <Tag tone={roleTone(member.role)} dot style={{}}>
          {member.role ?? "—"}
        </Tag>
        <span style={{ fontSize: 11, color: "#94a3b8" }}>
          {project?.name ?? (member.role === "ADMIN" ? "Butterfly 운영진" : "프로젝트 미선택")}
        </span>
      </div>

      <div style={{ padding: 6 }}>
        {items.map((it, i) =>
          it.divider ? (
            <div
              key={`divider-${i}`}
              style={{ height: 1, background: "#f1f5f9", margin: "4px 0" }}
            />
          ) : (
            <button
              key={it.label}
              onClick={it.onClick}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 10,
                width: "100%",
                padding: "9px 12px",
                background: "transparent",
                border: 0,
                borderRadius: 6,
                fontSize: 13,
                fontFamily: "inherit",
                color: it.danger ? "#b91c1c" : "#334155",
                cursor: "pointer",
                textAlign: "left",
              }}
              onMouseEnter={(e) =>
                (e.currentTarget.style.background = it.danger ? "#fef2f2" : "#f8fafc")
              }
              onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
            >
              <span style={{ color: it.danger ? "#b91c1c" : "#64748b" }}>{it.icon}</span>
              {it.label}
            </button>
          ),
        )}
      </div>
    </div>
  );
}

function roleTone(role?: string): "info" | "success" | "warning" | "default" {
  if (role === "ADMIN") return "warning";
  if (role === "OWNER") return "info";
  if (role === "EDITOR") return "success";
  return "default";
}
