import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";
import MobilePreview from "./MobilePreview.jsx";

function CampaignDetail({ id, onBack }) {
  const { CAMPAIGNS } = CRM_DATA;
  const c = CAMPAIGNS.find(x => x.id === id) || CAMPAIGNS[0];
  const sent = c.sent || 0;
  const openRate = parseFloat(c.openRate) || 0;
  const opened = Math.floor(sent * openRate / 100);
  const clicked = Math.floor(opened * 0.28);
  const converted = Math.floor(clicked * 0.22);

  return (
    <div>
      <PageHeader
        back="캠페인 목록" onBack={onBack}
        title={c.name}
        subtitle={<span style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontFamily: "var(--font-mono)", color: "#94a3b8" }}>{c.id}</span>
          <CampaignStatus status={c.status} />
          <ChannelBadge channel={c.channel} />
        </span>}
        actions={<>
          <Button variant="ghost" size="md" icon={Icon.copy(12)}>복제</Button>
          <Button variant="secondary" size="md" icon={Icon.edit(12)}>수정</Button>
          {c.status === "success" && <Button variant="primaryStrong" size="md" icon={Icon.analytics ? Icon.analytics(12) : null}>상세 리포트</Button>}
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="발송 수" value={fmtNum(sent)} sub={c.status === "success" ? "완료" : "예정"} />
        <StatCard label="오픈" value={fmtNum(opened)} delta={c.openRate} trend="up" />
        <StatCard label="클릭" value={fmtNum(clicked)} delta={((clicked/Math.max(sent,1))*100).toFixed(1) + "%"} trend="up" />
        <StatCard label="전환" value={fmtNum(converted)} delta={"₩" + fmtNum(converted * 62000)} trend="up" sub="기여 매출" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 340px", gap: 14 }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <Card title="기본 정보">
            <div style={{ display: "grid", gridTemplateColumns: "140px 1fr", rowGap: 12, columnGap: 16, fontSize: 13 }}>
              <div style={{ color: "#64748b" }}>캠페인 이름</div><div style={{ color: "#1f2937", fontWeight: 500 }}>{c.name}</div>
              <div style={{ color: "#64748b" }}>유형</div><div style={{ color: "#1f2937" }}>단발 발송</div>
              <div style={{ color: "#64748b" }}>설명</div><div style={{ color: "#1f2937" }}>{c.segment} 대상 리타게팅 메시지.</div>
              <div style={{ color: "#64748b" }}>발송 일정</div><div style={{ color: "#1f2937", fontFamily: "var(--font-mono)" }}>{c.date}</div>
              <div style={{ color: "#64748b" }}>생성자</div><div style={{ color: "#1f2937" }}>박지우 · 2026-04-20</div>
            </div>
          </Card>

          <Card title="세그먼트 및 예상 모수">
            <div style={{ display: "flex", alignItems: "center", gap: 16, padding: "10px 14px", background: "#f8fafc", borderRadius: 8, border: "1px solid #e2e8f0" }}>
              <div style={{ width: 34, height: 34, borderRadius: 8, background: "rgba(59,91,219,.10)", color: "#3b5bdb", display: "flex", alignItems: "center", justifyContent: "center" }}>{Icon.segments(16)}</div>
              <div style={{ flex: 1 }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{c.segment}</div>
                <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>스냅샷 모수 <b style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{fmtNum(sent)}</b>명 · 발송 시점 고정</div>
              </div>
              <Button variant="text" size="sm">세그먼트 보기 →</Button>
            </div>
          </Card>

          {c.status === "success" && (
            <Card title="시간대별 오픈 추이" subtitle="발송 후 24시간">
              <svg viewBox="0 0 700 140" style={{ width: "100%", height: 140 }}>
                <defs><linearGradient id="cd-g" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stopColor="#3b5bdb" stopOpacity=".28" /><stop offset="1" stopColor="#3b5bdb" stopOpacity="0" /></linearGradient></defs>
                {[0,1,2,3,4].map(i => <line key={i} x1="0" x2="700" y1={28*i+10} y2={28*i+10} stroke="#f1f5f9" strokeWidth="1" />)}
                <path d="M 10 120 Q 60 20 140 40 T 280 50 T 420 75 T 560 95 T 690 110 L 690 140 L 10 140 Z" fill="url(#cd-g)" />
                <path d="M 10 120 Q 60 20 140 40 T 280 50 T 420 75 T 560 95 T 690 110" fill="none" stroke="#3b5bdb" strokeWidth="2" />
              </svg>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "#94a3b8", marginTop: 4, fontFamily: "var(--font-mono)" }}>
                {["0h","4h","8h","12h","16h","20h","24h"].map(l => <span key={l}>{l}</span>)}
              </div>
            </Card>
          )}

          <Card title="발송 로그" subtitle="최근 10건">
            <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
              <div style={{ display: "grid", gridTemplateColumns: "1.4fr 1.4fr 1fr 1fr", padding: "10px 14px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
                <div>시각</div><div>수신자</div><div>상태</div><div>이벤트</div>
              </div>
              {[
                { t: "14:00:02", u: "이서연", s: "success", e: "오픈" },
                { t: "14:00:02", u: "최유진", s: "success", e: "클릭" },
                { t: "14:00:01", u: "정민재", s: "success", e: "—" },
                { t: "14:00:01", u: "한도윤", s: "success", e: "오픈" },
                { t: "14:00:00", u: "배소희", s: "fail", e: "잘못된 토큰" },
              ].map((r,i,a) => (
                <div key={i} style={{ display: "grid", gridTemplateColumns: "1.4fr 1.4fr 1fr 1fr", padding: "10px 14px", alignItems: "center", fontSize: 12, borderBottom: i<a.length-1 ? "1px solid #f1f5f9" : "none" }}>
                  <div style={{ color: "#64748b", fontFamily: "var(--font-mono)" }}>{r.t}</div>
                  <div style={{ color: "#1f2937" }}>{r.u}</div>
                  <div><Tag tone={r.s === "success" ? "success" : "error"} dot>{r.s === "success" ? "성공" : "실패"}</Tag></div>
                  <div style={{ color: "#64748b" }}>{r.e}</div>
                </div>
              ))}
            </div>
          </Card>
        </div>

        <div style={{ position: "sticky", top: 0 }}>
          <Card title="템플릿 미리보기">
            <MobilePreview
              title="{{name}}님, 장바구니 상품이 기다리고 있어요"
              body="담아두신 상품 2개에 오늘만 15% 쿠폰이 적용됩니다. 지금 확인해보세요 →"
            />
            <div style={{ marginTop: 12, fontSize: 11, color: "#64748b", textAlign: "center" }}>장바구니 이탈 리마인더 · FCM</div>
          </Card>
        </div>
      </div>
    </div>
  );
}

export default CampaignDetail;
