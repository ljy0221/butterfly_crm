import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function EventDetail({ event, onBack }) {
  const { PURCHASE_SCHEMA } = CRM_DATA;
  const schema = PURCHASE_SCHEMA; // always show purchase schema as the demo detail
  const [editing, setEditing] = React.useState(null);

  return (
    <div>
      <PageHeader
        back="이벤트" onBack={onBack}
        title={<span style={{ fontFamily: "var(--font-mono)", fontSize: 22 }}>{event.name}</span>}
        subtitle={`${event.type} 이벤트 · 속성 ${event.props}개 · ${fmtNum(event.volume)} 건 / 30일`}
        actions={<>
          <Button variant="ghost" size="md" icon={Icon.edit(12)}>이벤트 편집</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.plus(12)}>속성 추가</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="30일 수집량" value={fmtNum(event.volume)} delta="18.2%" trend="up" />
        <StatCard label="수집 성공률" value={event.rate} sub="최근 24시간" />
        <StatCard label="고유 사용자" value="184,210" delta="2.1%" trend="up" />
        <StatCard label="마지막 수신" value={event.last} sub="PST 기준" />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 380px", gap: 14 }}>
        <Card title="속성 스키마" subtitle="이벤트 데이터 JSON 구조" action={
          <Button variant="text" size="sm" icon={Icon.plus(12)}>속성 추가</Button>
        }>
          <div style={{ border: "1px solid #e2e8f0", borderRadius: 8, overflow: "hidden" }}>
            <div style={{ display: "grid", gridTemplateColumns: "1.2fr 0.7fr 0.5fr 1.3fr 60px", padding: "10px 14px", background: "#f8fafc", fontSize: 11, color: "#64748b", fontWeight: 600, borderBottom: "1px solid #e2e8f0" }}>
              <div>속성 이름</div><div>타입</div><div>필수</div><div>예시값</div><div></div>
            </div>
            {schema.map((s, i) => (
              <div key={s.key} style={{
                display: "grid", gridTemplateColumns: "1.2fr 0.7fr 0.5fr 1.3fr 60px",
                padding: "11px 14px", alignItems: "center", fontSize: 12,
                borderBottom: i < schema.length - 1 ? "1px solid #f1f5f9" : "none",
                background: editing === s.key ? "#eff3fe" : "#fff",
              }}>
                <div style={{ fontFamily: "var(--font-mono)", color: "#1f2937", fontWeight: 500 }}>{s.key}</div>
                <div><Tag tone={s.type === "string" ? "info" : s.type === "number" ? "soft" : "default"}>{s.type}</Tag></div>
                <div>{s.required ? <span style={{ color: "#16a34a", display: "inline-flex" }}>{Icon.check(13)}</span> : <span style={{ color: "#cbd5e1" }}>—</span>}</div>
                <div style={{ fontFamily: "var(--font-mono)", color: "#64748b", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{s.example}</div>
                <div style={{ display: "flex", gap: 6, justifyContent: "flex-end" }}>
                  <button onClick={() => setEditing(editing === s.key ? null : s.key)} style={{ border: 0, background: "transparent", color: "#94a3b8", cursor: "pointer" }}>{Icon.edit(13)}</button>
                </div>
              </div>
            ))}
          </div>
          <div style={{ marginTop: 14, padding: 12, background: "#fffbeb", border: "1px solid #fde68a", borderRadius: 8, fontSize: 12, color: "#92400e", display: "flex", gap: 10, alignItems: "flex-start" }}>
            <span style={{ fontSize: 14 }}>⚠</span>
            <div>
              <div style={{ fontWeight: 600, marginBottom: 2 }}>스키마 변경 시 주의</div>
              속성 이름이나 타입을 바꾸면 이 이벤트를 사용하는 세그먼트 3개의 예상 모수가 재계산됩니다.
            </div>
          </div>
        </Card>

        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <Card title="최근 수신 샘플" subtitle="실시간 · 최근 5건">
            <div style={{ fontFamily: "var(--font-mono)", fontSize: 11, background: "#0f172a", color: "#e2e8f0", borderRadius: 8, padding: 14, lineHeight: 1.7, overflow: "auto" }}>
              <div style={{ color: "#94a3b8", marginBottom: 6 }}>// {event.last}</div>
              {`{`}<br/>
              <span style={{ paddingLeft: 14 }}><span style={{ color: "#7dd3fc" }}>"order_id"</span>: <span style={{ color: "#86efac" }}>"SO-2026-041829"</span>,</span><br/>
              <span style={{ paddingLeft: 14 }}><span style={{ color: "#7dd3fc" }}>"total_amount"</span>: <span style={{ color: "#fbbf24" }}>184000</span>,</span><br/>
              <span style={{ paddingLeft: 14 }}><span style={{ color: "#7dd3fc" }}>"currency"</span>: <span style={{ color: "#86efac" }}>"KRW"</span>,</span><br/>
              <span style={{ paddingLeft: 14 }}><span style={{ color: "#7dd3fc" }}>"payment_method"</span>: <span style={{ color: "#86efac" }}>"card"</span>,</span><br/>
              <span style={{ paddingLeft: 14 }}><span style={{ color: "#7dd3fc" }}>"is_first_order"</span>: <span style={{ color: "#f472b6" }}>true</span></span><br/>
              {`}`}
            </div>
          </Card>
          <Card title="사용 중인 세그먼트" subtitle="이 이벤트 기반">
            {["VIP · 최근 60일 재구매 없음","장바구니 이탈 고객","여름 시즌 구매 예상"].map(s => (
              <div key={s} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "8px 0", borderBottom: "1px dashed #f1f5f9", fontSize: 12 }}>
                <span style={{ color: "#1f2937", fontWeight: 500 }}>{s}</span>
                <span style={{ color: "#2563eb", fontWeight: 600 }}>→</span>
              </div>
            ))}
          </Card>
        </div>
      </div>
    </div>
  );
}

export default EventDetail;
