import React from "react";
import Card from "../../components/common/Card.jsx";

function MobilePreview({ title, body }) {
  return (
    <Card title="모바일 미리보기" subtitle="FCM 푸시 알림">
      <div style={{ background: "linear-gradient(180deg,#e2e8f0 0%, #cbd5e1 100%)", borderRadius: 28, padding: 14, position: "relative" }}>
        <div style={{ background: "#fff", borderRadius: 20, minHeight: 360, padding: "14px 12px", display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ fontSize: 10, color: "#94a3b8", textAlign: "center", fontWeight: 500, fontVariantNumeric: "tabular-nums" }}>10:00  ·  월요일, 4월 25일</div>
          <div style={{ background: "rgba(255,255,255,.9)", border: "1px solid #e2e8f0", borderRadius: 14, padding: 12, boxShadow: "0 1px 4px rgba(0,0,0,.04)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
              <div style={{ width: 18, height: 18, borderRadius: 4, background: "#3b5bdb", color: "#fff", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 10, fontWeight: 700 }}>S</div>
              <span style={{ fontSize: 10, color: "#64748b", fontWeight: 600 }}>STUDIO SEL</span>
              <span style={{ fontSize: 9, color: "#94a3b8", marginLeft: "auto" }}>지금</span>
            </div>
            <div style={{ fontSize: 12, fontWeight: 700, color: "#1f2937", marginBottom: 2 }}>{title}</div>
            <div style={{ fontSize: 11, color: "#334155", lineHeight: 1.45 }}>{body}</div>
          </div>
        </div>
      </div>
    </Card>
  );
}

export default MobilePreview;
