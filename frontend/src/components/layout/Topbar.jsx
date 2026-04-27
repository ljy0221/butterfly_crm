import React from "react";
import { Icon } from "../common/Icon.jsx";
import Avatar from "../common/Avatar.jsx";
import Button from "../common/Button.jsx";
import { openModal } from "../../lib/ui-bus.js";
import { CRM_DATA } from "../../data/mock.js";
import CommandPalette from "./CommandPalette.jsx";
import NotificationsPanel from "./NotificationsPanel.jsx";
import UserMenuModal from "./UserMenuModal.jsx";

export default function Topbar({ crumbs, onToggleSidebar, onNavigate }) {
  const openCommandPalette = React.useCallback(
    () => openModal(({ close }) => <CommandPalette close={close} onNavigate={onNavigate} />),
    [onNavigate]
  );

  React.useEffect(() => {
    const onKey = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        openCommandPalette();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [openCommandPalette]);

  const openNotifications = () =>
    openModal(({ close }) => <NotificationsPanel close={close} onNavigate={onNavigate} />);
  const openUserMenu = () =>
    openModal(({ close }) => <UserMenuModal close={close} onNavigate={onNavigate} />);

  return (
    <header
      style={{
        height: 56,
        borderBottom: "1px solid #e2e8f0",
        display: "flex",
        alignItems: "center",
        padding: "0 24px",
        background: "#fff",
        flex: "none",
      }}
    >
      <button
        onClick={onToggleSidebar}
        title="사이드바 접기"
        style={{
          width: 32, height: 32,
          border: "1px solid #e2e8f0", borderRadius: 8, background: "#fff",
          display: "flex", alignItems: "center", justifyContent: "center",
          color: "#64748b", cursor: "pointer",
          marginRight: 14,
        }}
      >
        {Icon.sidebar(14)}
      </button>

      <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12, color: "#64748b" }}>
        {crumbs.map((c, i) => (
          <React.Fragment key={i}>
            {i > 0 && <span style={{ color: "#cbd5e1" }}>/</span>}
            <span
              onClick={() => {
                if (i === 1 && onNavigate) onNavigate(c);
              }}
              style={{
                color: i === crumbs.length - 1 ? "#1f2937" : "#64748b",
                fontWeight: i === crumbs.length - 1 ? 600 : 400,
                cursor: i === 1 ? "pointer" : "default",
              }}
            >
              {c}
            </span>
          </React.Fragment>
        ))}
      </div>

      <div style={{ flex: 1 }} />

      <div
        onClick={openCommandPalette}
        style={{
          display: "flex", alignItems: "center", gap: 8,
          height: 32, padding: "0 12px", borderRadius: 8,
          background: "#f5f7fb", width: 260,
          cursor: "pointer",
        }}
      >
        <span style={{ color: "#94a3b8", display: "flex" }}>{Icon.search(14)}</span>
        <span style={{ flex: 1, fontSize: 12, color: "#94a3b8" }}>
          고객, 이벤트, 캠페인 검색
        </span>
        <span
          style={{
            fontSize: 10, color: "#94a3b8",
            border: "1px solid #e2e8f0",
            padding: "1px 5px", borderRadius: 4, background: "#fff",
          }}
        >
          ⌘K
        </span>
      </div>

      <div style={{ width: 12 }} />

      <button
        onClick={openNotifications}
        title="알림"
        style={{
          width: 32, height: 32,
          border: "1px solid #e2e8f0", borderRadius: 8, background: "#fff",
          display: "flex", alignItems: "center", justifyContent: "center",
          color: "#64748b", cursor: "pointer", position: "relative",
        }}
      >
        {Icon.bell(16)}
        <span
          style={{
            position: "absolute", top: 6, right: 6,
            width: 7, height: 7, borderRadius: 999,
            background: "#ef4444", border: "2px solid #fff",
          }}
        />
      </button>

      <div style={{ width: 12 }} />

      <Button
        variant="primaryStrong"
        size="md"
        icon={Icon.plus(14)}
        onClick={() => onNavigate && onNavigate("캠페인-신규")}
      >
        새 캠페인
      </Button>

      <div style={{ width: 12 }} />

      <span onClick={openUserMenu} style={{ cursor: "pointer" }}>
        <Avatar name={CRM_DATA.USER.name} size={30} />
      </span>
    </header>
  );
}
