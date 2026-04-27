import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";
import SdkGuideModal from "./SdkGuideModal.jsx";
import AddEventCategoryModal from "./AddEventCategoryModal.jsx";

function Events({ onNavigate }) {
  const onOpenEvent = (r) => onNavigate("이벤트-상세:" + r.name);
  const { EVENT_CATEGORIES } = CRM_DATA;
  const [filter, setFilter] = React.useState("all");
  const [query, setQuery] = React.useState("");
  const filtered = (filter === "all" ? EVENT_CATEGORIES : EVENT_CATEGORIES.filter(e => e.type === filter))
    .filter(e => !query || e.name.toLowerCase().includes(query.toLowerCase()));

  const statusTone = s => s === "정상" ? "success" : s === "경고" ? "warning" : "error";

  return (
    <div>
      <PageHeader
        title="이벤트"
        subtitle="수집 중인 이벤트 카테고리와 속성 스키마를 관리합니다."
        actions={<>
          <Button variant="secondary" size="md" icon={Icon.copy(12)} onClick={() => openModal(({close}) => <SdkGuideModal close={close} />)}>SDK 설치 가이드</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.plus(12)} onClick={() => openModal(({close}) => <AddEventCategoryModal close={close} />)}>이벤트 카테고리 추가</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="총 이벤트 카테고리" value="10" sub="기본 5 · 커스텀 5" />
        <StatCard label="30일 누적 수집" value="8.8M" delta="22.4%" trend="up" sub="이벤트 수" />
        <StatCard label="수집 성공률" value="96.2%" delta="0.4%p" trend="up" sub="지난 주 대비" />
        <StatCard label="이상 감지" value="2" delta="신규 1" trend="flat" sub="검토 필요" />
      </div>

      <Card>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
          <div style={{ display: "flex", gap: 4, padding: 3, background: "#f1f5f9", borderRadius: 8 }}>
            {[["all","전체"],["기본","기본"],["커스텀","커스텀"]].map(([k, l]) => (
              <button key={k} onClick={() => setFilter(k)} style={{
                padding: "5px 12px", fontSize: 12, fontWeight: 500, border: 0, borderRadius: 6,
                background: filter === k ? "#fff" : "transparent", color: filter === k ? "#1f2937" : "#64748b",
                cursor: "pointer", fontFamily: "inherit",
                boxShadow: filter === k ? "0 1px 2px rgba(0,0,0,.06)" : "none",
              }}>{l}</button>
            ))}
          </div>
          <SearchInput placeholder="이벤트명 검색 (예: purchase)" value={query} onChange={setQuery} width={280} />
          <Button variant="ghost" size="sm" icon={Icon.filter(12)} onClick={() => toast("필터 메뉴는 곧 제공됩니다", "info")}>필터</Button>
        </div>

        <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "2fr 0.8fr 1.2fr 1fr 1fr 0.9fr 40px", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
            <div>이벤트명</div><div>유형</div><div>30일 수집량</div><div>수집 성공률</div><div>최근 수집</div><div>상태</div><div></div>
          </div>
          {filtered.map((r, i) => (
            <div key={r.name} onClick={() => onOpenEvent(r)} style={{
              display: "grid", gridTemplateColumns: "2fr 0.8fr 1.2fr 1fr 1fr 0.9fr 40px",
              padding: "14px 16px", alignItems: "center", fontSize: 13, cursor: "pointer",
              borderBottom: i < filtered.length - 1 ? "1px solid #f1f5f9" : "none",
              transition: "background 120ms",
            }}
              onMouseEnter={e => e.currentTarget.style.background = "#f8fafc"}
              onMouseLeave={e => e.currentTarget.style.background = "#fff"}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ width: 28, height: 28, borderRadius: 6, background: "#eff3fe", color: "#3b5bdb", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 12, fontWeight: 700, fontFamily: "var(--font-mono)" }}>
                  {r.name[0].toUpperCase()}
                </span>
                <div>
                  <div style={{ fontFamily: "var(--font-mono)", color: "#1f2937", fontSize: 13, fontWeight: 500 }}>{r.name}</div>
                  <div style={{ fontSize: 11, color: "#94a3b8" }}>속성 {r.props}개</div>
                </div>
              </div>
              <div><Tag tone={r.type === "기본" ? "info" : "soft"}>{r.type}</Tag></div>
              <div style={{ fontVariantNumeric: "tabular-nums", color: "#1f2937" }}>{fmtNum(r.volume)}</div>
              <div style={{ fontVariantNumeric: "tabular-nums", color: "#1f2937" }}>{r.rate}</div>
              <div style={{ color: "#64748b", fontSize: 12 }}>{r.last}</div>
              <div><Tag tone={statusTone(r.status)} dot>{r.status}</Tag></div>
              <div style={{ color: "#cbd5e1", textAlign: "right" }}>{Icon.chevron(12)}</div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default Events;
