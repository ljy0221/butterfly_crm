import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function JourneyCanvas() {
  // Mini flow diagram: trigger -> wait -> send -> branch
  const Node = ({ x, y, w=140, title, kind, sub, tone }) => {
    const bg = { trigger: "#eff3fe", action: "#fff", wait: "#fef3c7", branch: "#f1f5f9", end: "#f8fafc" }[tone || kind];
    const border = { trigger: "#3b5bdb", action: "#e2e8f0", wait: "#fbbf24", branch: "#cbd5e1", end: "#e2e8f0" }[tone || kind];
    return (
      <g transform={`translate(${x},${y})`}>
        <rect width={w} height="44" rx="8" fill={bg} stroke={border} strokeWidth="1.2" />
        <text x="10" y="17" fontSize="9" fill="#64748b" fontWeight="600">{kind.toUpperCase()}</text>
        <text x="10" y="32" fontSize="11" fill="#1f2937" fontWeight="600">{title}</text>
      </g>
    );
  };
  return (
    <svg viewBox="0 0 640 200" style={{ width: "100%", height: 180 }}>
      <defs>
        <marker id="arr" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#94a3b8" /></marker>
      </defs>
      <Node x={10} y={80} title="add_to_cart" kind="trigger" />
      <path d="M150 102 L180 102" stroke="#94a3b8" strokeWidth="1.5" markerEnd="url(#arr)" />
      <Node x={180} y={80} title="30분 대기" kind="wait" />
      <path d="M320 102 L350 102" stroke="#94a3b8" strokeWidth="1.5" markerEnd="url(#arr)" />
      <Node x={350} y={40} title="FCM 1차 알림" kind="action" />
      <Node x={350} y={120} title="이메일 백업" kind="action" />
      <path d="M345 102 L350 62" stroke="#94a3b8" strokeWidth="1.5" fill="none" markerEnd="url(#arr)" />
      <path d="M345 102 L350 142" stroke="#94a3b8" strokeWidth="1.5" fill="none" markerEnd="url(#arr)" />
      <path d="M490 62 L540 100" stroke="#94a3b8" strokeWidth="1.5" fill="none" markerEnd="url(#arr)" />
      <Node x={540} y={80} w={90} title="종료" kind="end" />
    </svg>
  );
}

function Journey() {
  const { JOURNEYS } = CRM_DATA;
  return (
    <div>
      <PageHeader
        title="자동화 플로우"
        subtitle="이벤트 트리거 기반 다단계 메시지 저니를 구성합니다."
        actions={<>
          <Button variant="secondary" size="md" icon={Icon.copy(12)}>템플릿 탐색</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.plus(12)}>새 플로우</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="운영 중 플로우" value="3" />
        <StatCard label="이번 주 자동 발송" value="28,420" delta="12.2%" trend="up" />
        <StatCard label="자동화 오픈률" value="42.8%" delta="3.4%p" trend="up" />
        <StatCard label="전환 매출" value="₩62.4M" delta="18%" trend="up" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 20 }}>
        {JOURNEYS.slice(0, 2).map(j => (
          <Card key={j.name} padding={0}>
            <div style={{ padding: 20, borderBottom: "1px solid #f1f5f9", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)" }}>{j.name}</div>
                <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2 }}>트리거 · <span style={{ fontFamily: "var(--font-mono)" }}>{j.trigger}</span> · 수정 {j.updated}</div>
              </div>
              <CampaignStatus status={j.status} />
            </div>
            <div style={{ padding: 20, background: "#fafbfe" }}>
              <JourneyCanvas />
            </div>
            <div style={{ padding: "12px 20px", display: "flex", alignItems: "center", justifyContent: "space-between", fontSize: 12 }}>
              <div style={{ color: "#64748b" }}>활성 등록 <b style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{fmtNum(j.enrolled)}</b>명</div>
              <span style={{ color: "#2563eb", fontWeight: 600, cursor: "pointer" }}>편집 →</span>
            </div>
          </Card>
        ))}
      </div>

      <Card title="전체 플로우" subtitle={`${JOURNEYS.length}개`}>
        <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "2.4fr 1fr 1fr 1fr 0.8fr", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
            <div>플로우명</div><div>트리거</div><div>등록 인원</div><div>수정</div><div>상태</div>
          </div>
          {JOURNEYS.map((j, i) => (
            <div key={j.name} style={{ display: "grid", gridTemplateColumns: "2.4fr 1fr 1fr 1fr 0.8fr", padding: "14px 16px", alignItems: "center", fontSize: 13, borderBottom: i < JOURNEYS.length - 1 ? "1px solid #f1f5f9" : "none" }}>
              <div style={{ color: "#1f2937", fontWeight: 500 }}>{j.name}</div>
              <div style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "#64748b" }}>{j.trigger}</div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{fmtNum(j.enrolled)}</div>
              <div style={{ color: "#64748b", fontSize: 12 }}>{j.updated}</div>
              <div><CampaignStatus status={j.status} /></div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default Journey;
