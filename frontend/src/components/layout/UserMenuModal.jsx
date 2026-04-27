import React from "react";
import { Icon } from "../common/Icon.jsx";
import Avatar from "../common/Avatar.jsx";
import Tag from "../common/Tag.jsx";
import { toast, confirmDialog } from "../../lib/ui-bus.js";
import { CRM_DATA } from "../../data/mock.js";

export default function UserMenuModal({ close, onNavigate }) {
  const u = CRM_DATA.USER;

  const items = [
    { l: "프로필 설정", icon: Icon.edit(13),   onClick: () => { close(); onNavigate("설정"); } },
    { l: "API 키 관리", icon: Icon.copy(13),   onClick: () => { close(); onNavigate("설정"); } },
    { l: "도움말 센터", icon: Icon.search(13), onClick: () => { close(); toast("도움말 센터를 새 창에서 엽니다", "info"); } },
    { divider: true },
    {
      l: "로그아웃", icon: Icon.back(13), danger: true,
      onClick: async () => {
        close();
        const ok = await confirmDialog({
          title: "로그아웃하시겠습니까?",
          desc: "현재 세션이 종료되고 Access Token이 블랙리스트에 등록됩니다.",
          ok: "로그아웃",
          danger: true,
        });
        if (ok) toast("로그아웃되었습니다", "info");
      },
    },
  ];

  return (
    <div
      style={{
        position: "fixed", top: 60, right: 24,
        width: 260, background: "#fff", borderRadius: 12,
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
      }}
    >
      <div
        style={{
          padding: "16px 18px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex", alignItems: "center", gap: 10,
        }}
      >
        <Avatar name={u.name} size={36} />
        <div>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#1f2937" }}>{u.name}</div>
          <div style={{ fontSize: 11, color: "#64748b" }}>{u.email}</div>
        </div>
      </div>

      <div
        style={{
          padding: "10px 14px",
          background: "#fafbfe",
          borderBottom: "1px solid #f1f5f9",
          display: "flex", alignItems: "center", gap: 8,
        }}
      >
        <Tag tone="warning" dot>{u.role}</Tag>
        <span style={{ fontSize: 11, color: "#94a3b8" }}>프로젝트 소유자</span>
      </div>

      <div style={{ padding: 6 }}>
        {items.map((it, i) =>
          it.divider ? (
            <div key={i} style={{ height: 1, background: "#f1f5f9", margin: "4px 0" }} />
          ) : (
            <button
              key={i}
              onClick={it.onClick}
              style={{
                display: "flex", alignItems: "center", gap: 10, width: "100%",
                padding: "9px 12px", background: "transparent",
                border: 0, borderRadius: 6,
                fontSize: 13, fontFamily: "inherit",
                color: it.danger ? "#b91c1c" : "#334155",
                cursor: "pointer", textAlign: "left",
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = it.danger ? "#fef2f2" : "#f8fafc")}
              onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
            >
              <span style={{ color: it.danger ? "#b91c1c" : "#64748b" }}>{it.icon}</span>
              {it.l}
            </button>
          )
        )}
      </div>
    </div>
  );
}
