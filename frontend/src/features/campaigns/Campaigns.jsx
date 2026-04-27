import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function Campaigns({ onNavigate }) {
  const onOpenWizard = () => onNavigate("캠페인-신규");
  const { CAMPAIGNS } = CRM_DATA;
  const [tab, setTab] = React.useState("all");
  const tabs = [
    { key: "all", label: "전체", count: CAMPAIGNS.length },
    { key: "success", label: "발송 완료", count: CAMPAIGNS.filter(c=>c.status==="success").length },
    { key: "send", label: "발송 중", count: CAMPAIGNS.filter(c=>c.status==="send").length },
    { key: "ready", label: "발송 대기", count: CAMPAIGNS.filter(c=>c.status==="ready").length },
    { key: "draft", label: "초안", count: CAMPAIGNS.filter(c=>c.status==="draft").length },
    { key: "fail", label: "실패", count: CAMPAIGNS.filter(c=>c.status==="fail").length },
  ];
  const rows = tab === "all" ? CAMPAIGNS : CAMPAIGNS.filter(c => c.status === tab);

  return (
    <div>
      <PageHeader
        title="캠페인"
        subtitle="세그먼트에 메시지를 발송합니다. 단발·예약·반복 발송을 모두 지원합니다."
        actions={<>
          <Button variant="secondary" size="md">초안 ({CAMPAIGNS.filter(c=>c.status==="draft").length})</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.plus(12)} onClick={onOpenWizard}>새 캠페인</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="이번 달 발송" value="542,180" delta="18.2%" trend="up" />
        <StatCard label="평균 오픈률" value="38.4%" delta="2.1%p" trend="up" />
        <StatCard label="전환 매출" value="₩184.2M" delta="8.4%" trend="up" sub="기여 매출" />
        <StatCard label="발송 실패" value="1,218" delta="0.8%" trend="down" sub="전체의 0.2%" />
      </div>

      <Card>
        <div style={{ display: "flex", alignItems: "center", gap: 2, marginBottom: 16, borderBottom: "1px solid #e2e8f0" }}>
          {tabs.map(t => (
            <button key={t.key} onClick={() => setTab(t.key)} style={{
              padding: "10px 14px", fontSize: 12, fontWeight: 500, border: 0, background: "transparent",
              color: tab === t.key ? "#1f2937" : "#64748b",
              borderBottom: tab === t.key ? "2px solid #3b5bdb" : "2px solid transparent",
              marginBottom: -1, cursor: "pointer", fontFamily: "inherit",
              display: "inline-flex", alignItems: "center", gap: 6,
            }}>
              {t.label}
              <span style={{ fontSize: 10, padding: "1px 6px", borderRadius: 999, background: tab === t.key ? "rgba(59,91,219,.1)" : "#f1f5f9", color: tab === t.key ? "#3b5bdb" : "#64748b", fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>{t.count}</span>
            </button>
          ))}
        </div>

        <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "2.4fr 1.4fr 0.8fr 0.9fr 0.8fr 1fr 0.9fr 40px", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
            <div>캠페인명</div><div>세그먼트</div><div>채널</div><div>발송수</div><div>오픈률</div><div>발송 일시</div><div>상태</div><div></div>
          </div>
          {rows.map((c, i) => (
            <div key={c.id} onClick={() => onNavigate("캠페인-상세:" + c.id)} style={{
              display: "grid", gridTemplateColumns: "2.4fr 1.4fr 0.8fr 0.9fr 0.8fr 1fr 0.9fr 40px",
              padding: "14px 16px", alignItems: "center", fontSize: 13, cursor: "pointer",
              borderBottom: i < rows.length - 1 ? "1px solid #f1f5f9" : "none",
            }}
              onMouseEnter={e => e.currentTarget.style.background = "#f8fafc"}
              onMouseLeave={e => e.currentTarget.style.background = "#fff"}
            >
              <div>
                <div style={{ color: "#1f2937", fontWeight: 500 }}>{c.name}</div>
                <div style={{ fontSize: 11, color: "#94a3b8", fontFamily: "var(--font-mono)", marginTop: 2 }}>{c.id}</div>
              </div>
              <div style={{ color: "#64748b", fontSize: 12, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{c.segment}</div>
              <div><ChannelBadge channel={c.channel} /></div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{fmtNum(c.sent)}</div>
              <div style={{ color: c.openRate === "—" ? "#94a3b8" : "#16a34a", fontVariantNumeric: "tabular-nums", fontWeight: 600 }}>{c.openRate}</div>
              <div style={{ color: "#64748b", fontSize: 12 }}>{c.date}</div>
              <div><CampaignStatus status={c.status} /></div>
              <div style={{ color: "#cbd5e1", textAlign: "right" }}>{Icon.dots(14)}</div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default Campaigns;
