import React from "react";
import { Icon } from "../common/Icon.jsx";
import Tag from "../common/Tag.jsx";
import { toast } from "../../lib/ui-bus";

const ITEMS = [
  { k: "대시보드", l: "대시보드로 이동", kind: "페이지" },
  { k: "이벤트", l: "이벤트 목록", kind: "페이지" },
  { k: "고객", l: "고객 목록", kind: "페이지" },
  { k: "세그먼트", l: "세그먼트 목록", kind: "페이지" },
  { k: "세그먼트-신규", l: "새 세그먼트 만들기", kind: "액션" },
  { k: "캠페인", l: "캠페인 목록", kind: "페이지" },
  { k: "캠페인-신규", l: "새 캠페인 만들기", kind: "액션" },
  { k: "자동화", l: "자동화 플로우", kind: "페이지" },
  { k: "템플릿", l: "메시지 템플릿", kind: "페이지" },
  { k: "분석", l: "분석 리포트", kind: "페이지" },
  { k: "설정", l: "설정", kind: "페이지" },
  { k: "이벤트-상세:purchase", l: "purchase 이벤트 스키마", kind: "이벤트" },
  { k: "캠페인-상세:cmp_4821", l: "장바구니 이탈 리타게팅 · 4월", kind: "캠페인" },
];

export default function CommandPalette({ close, onNavigate }) {
  const [q, setQ] = React.useState("");
  const filtered = q
    ? ITEMS.filter((it) => it.l.toLowerCase().includes(q.toLowerCase()) || it.kind.includes(q))
    : ITEMS;
  const [sel, setSel] = React.useState(0);

  React.useEffect(() => setSel(0), [q]);

  const go = (it) => {
    close();
    onNavigate(it.k);
    toast(`${it.l}`, "info");
  };

  return (
    <div
      style={{
        width: 560,
        background: "#fff",
        borderRadius: 14,
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
      }}
      onKeyDown={(e) => {
        if (e.key === "ArrowDown") { e.preventDefault(); setSel((s) => Math.min(s + 1, filtered.length - 1)); }
        if (e.key === "ArrowUp")   { e.preventDefault(); setSel((s) => Math.max(s - 1, 0)); }
        if (e.key === "Enter" && filtered[sel]) { e.preventDefault(); go(filtered[sel]); }
      }}
    >
      <div
        style={{
          padding: "14px 18px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex", alignItems: "center", gap: 10,
        }}
      >
        <span style={{ color: "#94a3b8" }}>{Icon.search(16)}</span>
        <input
          autoFocus
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="무엇이든 검색하거나 이동하세요…"
          style={{
            flex: 1, border: 0, outline: 0, background: "transparent",
            fontFamily: "inherit", fontSize: 15,
          }}
        />
        <span
          style={{
            fontSize: 10, color: "#94a3b8",
            border: "1px solid #e2e8f0",
            padding: "2px 6px", borderRadius: 4,
          }}
        >
          ESC
        </span>
      </div>
      <div style={{ maxHeight: 360, overflowY: "auto", padding: 6 }}>
        {filtered.length === 0 && (
          <div style={{ padding: 24, textAlign: "center", color: "#94a3b8", fontSize: 13 }}>
            결과 없음
          </div>
        )}
        {filtered.map((it, i) => (
          <div
            key={it.k}
            onClick={() => go(it)}
            onMouseEnter={() => setSel(i)}
            style={{
              padding: "10px 12px",
              display: "flex", alignItems: "center", gap: 10,
              background: i === sel ? "#eff3fe" : "transparent",
              borderRadius: 8,
              cursor: "pointer",
            }}
          >
            <Tag tone={it.kind === "액션" ? "info" : "soft"}>{it.kind}</Tag>
            <span style={{ fontSize: 13, color: "#1f2937", flex: 1 }}>{it.l}</span>
            {i === sel && <span style={{ color: "#94a3b8" }}>{Icon.chevron(12)}</span>}
          </div>
        ))}
      </div>
      <div
        style={{
          padding: "10px 16px",
          borderTop: "1px solid #f1f5f9",
          background: "#f8fafc",
          fontSize: 11, color: "#94a3b8",
          display: "flex", gap: 14,
        }}
      >
        <span>↑↓ 이동</span>
        <span>⏎ 선택</span>
        <span>ESC 닫기</span>
      </div>
    </div>
  );
}
