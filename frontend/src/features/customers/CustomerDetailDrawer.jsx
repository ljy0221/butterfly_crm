import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";
import AddToSegmentModal from "./AddToSegmentModal.jsx";

function CustomerDetailDrawer({ close, customer: c }) {
  return (
    <div style={{ position: "fixed", top: 0, right: 0, bottom: 0, width: 540, background: "#fff", boxShadow: "-8px 0 32px -8px rgba(0,0,0,.2)", display: "flex", flexDirection: "column", animation: "slideIn 240ms cubic-bezier(.2,.7,.3,1)" }}>
      <style>{`@keyframes slideIn { from { transform: translateX(100%); } to { transform: translateX(0); } }`}</style>
      <div style={{ padding: "20px 22px", borderBottom: "1px solid #f1f5f9", display: "flex", alignItems: "flex-start", gap: 14 }}>
        <Avatar name={c.name} size={48} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 18, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)" }}>{c.name}</div>
          <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>{c.email}</div>
          <div style={{ marginTop: 8, display: "flex", gap: 6 }}>
            <Tag tone={c.tier === "VIP" ? "warning" : c.tier === "Regular" ? "info" : "default"} dot>{c.tier}</Tag>
            <Tag tone="soft">{c.events} 이벤트</Tag>
            <span style={{ fontSize: 11, color: "#94a3b8", fontFamily: "var(--font-mono)" }}>{c.id}</span>
          </div>
        </div>
        <span onClick={close} style={{ color: "#94a3b8", cursor: "pointer" }}>{Icon.x(18)}</span>
      </div>
      <div style={{ flex: 1, overflowY: "auto", padding: 22 }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 20 }}>
          <div style={{ padding: 14, background: "#f8fafc", borderRadius: 10 }}>
            <div style={{ fontSize: 11, color: "#64748b" }}>누적 결제</div>
            <div style={{ fontSize: 20, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)", marginTop: 4 }}>₩{c.ltv}</div>
          </div>
          <div style={{ padding: 14, background: "#f8fafc", borderRadius: 10 }}>
            <div style={{ fontSize: 11, color: "#64748b" }}>최근 활동</div>
            <div style={{ fontSize: 20, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)", marginTop: 4 }}>{c.last}</div>
          </div>
        </div>

        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#1f2937", marginBottom: 10, fontFamily: "var(--font-display)" }}>최근 이벤트 타임라인</div>
          <div style={{ position: "relative", paddingLeft: 18 }}>
            <div style={{ position: "absolute", left: 5, top: 6, bottom: 6, width: 1, background: "#e2e8f0" }} />
            {[
              { t: "방금", e: "purchase", d: "린넨 블레이저 외 1건 · ₩184,000" },
              { t: "2시간 전", e: "page_view", d: "/product/12458 · 체류 42초" },
              { t: "어제", e: "add_to_cart", d: "린넨 블레이저 · 1개" },
              { t: "3일 전", e: "login", d: "iOS · Safari" },
              { t: "5일 전", e: "page_view", d: "/category/women" },
            ].map((ev, i) => (
              <div key={i} style={{ position: "relative", paddingBottom: 14, paddingLeft: 14 }}>
                <span style={{ position: "absolute", left: -13, top: 4, width: 9, height: 9, borderRadius: 999, background: "#3b5bdb", border: "2px solid #fff", boxShadow: "0 0 0 1px #e2e8f0" }} />
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 3 }}>
                  <span style={{ fontSize: 12, fontWeight: 600, fontFamily: "var(--font-mono)", color: "#3b5bdb" }}>{ev.e}</span>
                  <span style={{ fontSize: 11, color: "#94a3b8" }}>{ev.t}</span>
                </div>
                <div style={{ fontSize: 12, color: "#64748b" }}>{ev.d}</div>
              </div>
            ))}
          </div>
        </div>

        <div style={{ marginBottom: 20 }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#1f2937", marginBottom: 10, fontFamily: "var(--font-display)" }}>포함 세그먼트</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {["VIP · 최근 60일 재구매 없음", "장바구니 이탈 고객"].map(s => (
              <div key={s} style={{ padding: "10px 12px", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 12, color: "#1f2937", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <span>{s}</span>
                <span style={{ color: "#cbd5e1" }}>{Icon.chevron(12)}</span>
              </div>
            ))}
          </div>
        </div>

        <div>
          <div style={{ fontSize: 13, fontWeight: 700, color: "#1f2937", marginBottom: 10, fontFamily: "var(--font-display)" }}>수신 캠페인</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {[
              { n: "여름 프리오더 사전 안내", c: "FCM", s: "오픈" },
              { n: "VIP 전용 쿠폰 발송", c: "이메일", s: "클릭" },
              { n: "장바구니 이탈 리타게팅", c: "FCM", s: "미오픈" },
            ].map((x, i) => (
              <div key={i} style={{ padding: "10px 12px", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 12, display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ flex: 1, color: "#1f2937" }}>{x.n}</span>
                <ChannelBadge channel={x.c} />
                <Tag tone={x.s === "클릭" ? "success" : x.s === "오픈" ? "info" : "default"}>{x.s}</Tag>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div style={{ padding: "14px 22px", borderTop: "1px solid #f1f5f9", display: "flex", gap: 8 }}>
        <Button variant="secondary" size="md" icon={Icon.send(12)} onClick={() => toast("단건 발송 화면은 준비 중입니다", "info")} style={{ flex: 1 }}>단건 발송</Button>
        <Button variant="primaryStrong" size="md" icon={Icon.plus(12)} onClick={() => { openModal(({close:c2}) => <AddToSegmentModal close={c2} count={1} onDone={()=>{}} />); }} style={{ flex: 1 }}>세그먼트에 추가</Button>
      </div>
    </div>
  );
}

export default CustomerDetailDrawer;
