import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function Dashboard({ onNavigate }) {
  const { CAMPAIGNS } = CRM_DATA;
  const recent = CAMPAIGNS.slice(0, 5);
  return (
    <div>
      <PageHeader
        title="대시보드"
        subtitle="최근 30일 기준 · 2026.03.25 – 04.24"
        actions={<>
          <Button variant="secondary" size="sm" icon={Icon.calendar(12)}>최근 30일</Button>
          <Button variant="primaryStrong" size="sm" icon={Icon.plus(12)} onClick={() => onNavigate("캠페인-신규")}>새 캠페인</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="활성 고객" value="248,392" delta="12.4%" trend="up" sub="vs 지난 달" />
        <StatCard label="앱 푸시 오픈률" value="34.2%" delta="1.8%p" trend="up" sub="FCM 기준" />
        <StatCard label="전환 매출" value="₩184.2M" delta="8.4%" trend="up" sub="캠페인 기여" />
        <StatCard label="이탈 세그먼트" value="4,821" delta="0.3%" trend="down" sub="90일 비활성" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 14, marginBottom: 20 }}>
        <Card title="채널별 발송 추이" subtitle="일별 · 최근 14일" action={
          <div style={{ display: "flex", gap: 6 }}>
            {["FCM","이메일","SMS"].map((c,i) => <Tag key={c} tone={i===0?"info":"soft"} dot>{c}</Tag>)}
          </div>
        }>
          <svg viewBox="0 0 700 220" style={{ width: "100%", height: 220 }}>
            <defs>
              <linearGradient id="gBlue" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#3b5bdb" stopOpacity="0.18" />
                <stop offset="100%" stopColor="#3b5bdb" stopOpacity="0" />
              </linearGradient>
            </defs>
            {[0, 55, 110, 165, 220].map((y, i) => (
              <line key={i} x1="0" y1={y} x2="700" y2={y} stroke="#f1f5f9" />
            ))}
            {[0,100,200,300,400,500,600,700].map(x => (
              <text key={x} x={x} y="215" fontSize="9" fill="#94a3b8" textAnchor="middle">{`4/${10+Math.round(x/50)}`}</text>
            ))}
            <path d="M0,180 L50,160 L100,170 L150,130 L200,140 L250,100 L300,115 L350,80 L400,95 L450,60 L500,40 L550,55 L600,35 L650,45 L700,25"
              fill="none" stroke="#3b5bdb" strokeWidth="2" />
            <path d="M0,180 L50,160 L100,170 L150,130 L200,140 L250,100 L300,115 L350,80 L400,95 L450,60 L500,40 L550,55 L600,35 L650,45 L700,25 L700,200 L0,200 Z"
              fill="url(#gBlue)" />
            <path d="M0,190 L50,185 L100,188 L150,175 L200,180 L250,165 L300,170 L350,155 L400,162 L450,145 L500,138 L550,148 L600,135 L650,140 L700,125"
              fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeDasharray="4 3" />
            <path d="M0,195 L50,192 L100,193 L150,188 L200,190 L250,182 L300,185 L350,178 L400,180 L450,172 L500,168 L550,174 L600,170 L650,172 L700,165"
              fill="none" stroke="#cbd5e1" strokeWidth="1.5" />
          </svg>
        </Card>
        <Card title="채널별 발송 비중" subtitle="최근 30일">
          {[
            { name: "FCM 푸시", pct: 58, color: "#3b5bdb", count: "3,012,840" },
            { name: "이메일", pct: 24, color: "#2563eb", count: "1,248,200" },
            { name: "SMS / LMS", pct: 12, color: "#64748b", count: "621,480" },
            { name: "웹훅", pct: 6, color: "#94a3b8", count: "312,048" },
          ].map(c => (
            <div key={c.name} style={{ marginBottom: 14 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 6 }}>
                <span style={{ color: "#1f2937", fontWeight: 500 }}>{c.name}</span>
                <span style={{ color: "#64748b", fontVariantNumeric: "tabular-nums" }}>{c.pct}%</span>
              </div>
              <div style={{ height: 6, borderRadius: 3, background: "#f1f5f9" }}>
                <div style={{ height: "100%", width: `${c.pct}%`, background: c.color, borderRadius: 3 }} />
              </div>
              <div style={{ fontSize: 10, color: "#94a3b8", marginTop: 3, fontVariantNumeric: "tabular-nums" }}>{c.count} 건</div>
            </div>
          ))}
        </Card>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 14 }}>
        <Card title="최근 캠페인" subtitle="최근 발송 5건" action={
          <span onClick={() => onNavigate("캠페인")} style={{ fontSize: 12, color: "#2563eb", fontWeight: 600, cursor: "pointer" }}>전체 보기 →</span>
        }>
          <div style={{ margin: "-4px -4px" }}>
            <div style={{ display: "grid", gridTemplateColumns: "2.2fr 1fr 0.7fr 0.8fr 0.7fr 0.8fr", padding: "10px 4px", fontSize: 11, color: "#64748b", fontWeight: 600, borderBottom: "1px solid #f1f5f9" }}>
              <div>캠페인</div><div>세그먼트</div><div>채널</div><div>발송수</div><div>오픈률</div><div>상태</div>
            </div>
            {recent.map((c, i) => (
              <div key={c.id} style={{
                display: "grid", gridTemplateColumns: "2.2fr 1fr 0.7fr 0.8fr 0.7fr 0.8fr",
                padding: "12px 4px", alignItems: "center", fontSize: 12,
                borderBottom: i < recent.length - 1 ? "1px solid #f8fafc" : "none",
              }}>
                <div style={{ color: "#1f2937", fontWeight: 500 }}>{c.name}</div>
                <div style={{ color: "#64748b", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{c.segment}</div>
                <div><ChannelBadge channel={c.channel} /></div>
                <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{fmtNum(c.sent)}</div>
                <div style={{ color: "#16a34a", fontVariantNumeric: "tabular-nums", fontWeight: 600 }}>{c.openRate}</div>
                <div><CampaignStatus status={c.status} /></div>
              </div>
            ))}
          </div>
        </Card>
        <Card title="빠른 작업">
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {[
              { label: "세그먼트 만들기", desc: "조건 조합으로 타겟 정의", icon: Icon.segments(16), route: "세그먼트-신규" },
              { label: "캠페인 만들기", desc: "템플릿과 세그먼트 선택", icon: Icon.campaigns(16), route: "캠페인-신규" },
              { label: "이벤트 스키마 설정", desc: "속성 추가·타입 변경", icon: Icon.events(16), route: "이벤트" },
              { label: "성과 리포트", desc: "캠페인별 전환·오픈 분석", icon: Icon.analytics(16), route: "분석" },
            ].map(q => (
              <div key={q.label} onClick={() => onNavigate(q.route)} style={{
                display: "flex", alignItems: "center", gap: 12, padding: "10px 12px",
                border: "1px solid #e2e8f0", borderRadius: 8, cursor: "pointer",
                transition: "background 120ms", background: "#fff",
              }}
                onMouseEnter={e => e.currentTarget.style.background = "#f8fafc"}
                onMouseLeave={e => e.currentTarget.style.background = "#fff"}
              >
                <div style={{ width: 32, height: 32, borderRadius: 8, background: "rgba(59,91,219,.08)", color: "#3b5bdb", display: "flex", alignItems: "center", justifyContent: "center" }}>{q.icon}</div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: "#1f2937" }}>{q.label}</div>
                  <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 1 }}>{q.desc}</div>
                </div>
                <span style={{ color: "#cbd5e1" }}>{Icon.chevron(12)}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}

export default Dashboard;
