import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";
import CustomerDetailDrawer from "./CustomerDetailDrawer.jsx";
import AddToSegmentModal from "./AddToSegmentModal.jsx";

function Customers() {
  const { CUSTOMERS } = CRM_DATA;
  const tierTone = t => t === "VIP" ? "warning" : t === "Regular" ? "info" : t === "New" ? "success" : "default";
  const [query, setQuery] = React.useState("");
  const [tierFilter, setTierFilter] = React.useState("all");
  const [selected, setSelected] = React.useState(new Set());
  const filtered = CUSTOMERS.filter(c =>
    (!query || c.name.includes(query) || c.email.toLowerCase().includes(query.toLowerCase()) || c.id.includes(query))
    && (tierFilter === "all" || c.tier === tierFilter)
  );
  const toggle = (id) => setSelected(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n; });
  const allSelected = filtered.length > 0 && filtered.every(c => selected.has(c.id));
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(filtered.map(c => c.id)));

  const bulkDelete = async () => {
    const ok = await confirmDialog({ title: `${selected.size}명 삭제`, desc: "선택한 고객은 소프트 삭제됩니다. 이력은 유지됩니다.", ok: "삭제", danger: true });
    if (ok) { toast(`${selected.size}명을 삭제 처리했습니다`, "success"); setSelected(new Set()); }
  };
  const bulkAddSegment = () => openModal(({ close }) => <AddToSegmentModal close={close} count={selected.size} onDone={() => { setSelected(new Set()); }} />);

  return (
    <div>
      <PageHeader
        title="고객"
        subtitle="프로젝트에 유입된 전체 사용자를 조회합니다."
        actions={<>
          <Button variant="secondary" size="md" icon={Icon.copy(12)} onClick={() => toast("CSV 내보내기를 시작했습니다. 다운로드 링크를 이메일로 전송합니다.", "success")}>CSV 내보내기</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="전체 고객" value="284,911" delta="1,284 증가" trend="up" sub="지난 7일" />
        <StatCard label="활성 고객" value="248,392" sub="30일 내 이벤트" />
        <StatCard label="VIP 고객" value="8,291" delta="2.4%" trend="up" />
        <StatCard label="휴면 고객" value="48,210" delta="0.8%" trend="down" sub="90일+ 비활성" />
      </div>

      <Card>
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
          <SearchInput placeholder="이메일, 유저 ID, 이름으로 검색" value={query} onChange={setQuery} width={320} />
          <div style={{ display: "flex", gap: 4, padding: 3, background: "#f1f5f9", borderRadius: 8 }}>
            {[["all","전체"],["VIP","VIP"],["Regular","Regular"],["New","신규"],["Dormant","휴면"]].map(([k, l]) => (
              <button key={k} onClick={() => setTierFilter(k)} style={{
                padding: "5px 12px", fontSize: 11, fontWeight: 500, border: 0, borderRadius: 6,
                background: tierFilter === k ? "#fff" : "transparent", color: tierFilter === k ? "#1f2937" : "#64748b",
                cursor: "pointer", fontFamily: "inherit",
                boxShadow: tierFilter === k ? "0 1px 2px rgba(0,0,0,.06)" : "none",
              }}>{l}</button>
            ))}
          </div>
          <div style={{ flex: 1 }} />
          <span style={{ fontSize: 12, color: "#64748b" }}>총 <b style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{filtered.length}</b>명 {selected.size > 0 && <span style={{ color: "#3b5bdb" }}>· {selected.size} 선택</span>}</span>
        </div>

        {selected.size > 0 && (
          <div style={{ padding: "10px 14px", background: "#eff3fe", border: "1px solid #bfd1fe", borderRadius: 8, marginBottom: 12, display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ fontSize: 12, color: "#1e40af", fontWeight: 600 }}>{selected.size}명 선택됨</span>
            <Button variant="secondary" size="sm" icon={Icon.plus(12)} onClick={bulkAddSegment}>세그먼트에 추가</Button>
            <Button variant="ghost" size="sm" icon={Icon.send(12)} onClick={() => toast("캠페인 생성 화면으로 이동합니다", "info")}>캠페인 만들기</Button>
            <div style={{ flex: 1 }} />
            <Button variant="danger" size="sm" icon={Icon.trash(12)} onClick={bulkDelete}>삭제</Button>
            <span onClick={() => setSelected(new Set())} style={{ fontSize: 11, color: "#64748b", cursor: "pointer" }}>선택 해제</span>
          </div>
        )}

        <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
          <div style={{ display: "grid", gridTemplateColumns: "40px 2fr 2fr 0.8fr 0.8fr 1.2fr 0.8fr 40px", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
            <div><Checkbox checked={allSelected} onChange={toggleAll} /></div><div>고객</div><div>이메일</div><div>이벤트</div><div>최근 활동</div><div>누적 결제</div><div>등급</div><div></div>
          </div>
          {filtered.length === 0 && (
            <div style={{ padding: 60, textAlign: "center", color: "#94a3b8", fontSize: 13 }}>검색 결과가 없습니다</div>
          )}
          {filtered.map((c, i) => (
            <div key={c.id} onClick={() => openModal(({close}) => <CustomerDetailDrawer close={close} customer={c} />)} style={{
              display: "grid", gridTemplateColumns: "40px 2fr 2fr 0.8fr 0.8fr 1.2fr 0.8fr 40px",
              padding: "14px 16px", alignItems: "center", fontSize: 13, cursor: "pointer",
              borderBottom: i < filtered.length - 1 ? "1px solid #f1f5f9" : "none",
              background: selected.has(c.id) ? "#fafbfe" : "#fff",
              transition: "background 120ms",
            }}
              onMouseEnter={e => e.currentTarget.style.background = selected.has(c.id) ? "#eff3fe" : "#f8fafc"}
              onMouseLeave={e => e.currentTarget.style.background = selected.has(c.id) ? "#fafbfe" : "#fff"}
            >
              <div onClick={e => { e.stopPropagation(); toggle(c.id); }}><Checkbox checked={selected.has(c.id)} onChange={() => toggle(c.id)} /></div>
              <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                <Avatar name={c.name} size={28} />
                <div>
                  <div style={{ color: "#1f2937", fontWeight: 500 }}>{c.name}</div>
                  <div style={{ fontSize: 11, color: "#94a3b8", fontFamily: "var(--font-mono)" }}>{c.id}</div>
                </div>
              </div>
              <div style={{ color: "#64748b", fontSize: 12 }}>{c.email}</div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{c.events}</div>
              <div style={{ color: "#64748b", fontSize: 12 }}>{c.last}</div>
              <div style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums", fontWeight: 500 }}>₩{c.ltv}</div>
              <div><Tag tone={tierTone(c.tier)} dot>{c.tier}</Tag></div>
              <div onClick={e => e.stopPropagation()}>
                <Menu
                  trigger={<button style={{ width: 26, height: 26, border: 0, borderRadius: 6, background: "transparent", color: "#94a3b8", cursor: "pointer", display: "inline-flex", alignItems: "center", justifyContent: "center" }}>{Icon.dots(14)}</button>}
                  items={[
                    { label: "상세 보기", icon: Icon.edit(12), onClick: () => openModal(({close}) => <CustomerDetailDrawer close={close} customer={c} />) },
                    { label: "캠페인 보내기", icon: Icon.send(12), onClick: () => toast(`${c.name}에게 단건 캠페인 화면을 엽니다`, "info") },
                    { label: "세그먼트에 추가", icon: Icon.plus(12), onClick: () => openModal(({close}) => <AddToSegmentModal close={close} count={1} onDone={()=>{}} />) },
                    { divider: true },
                    { label: "고객 삭제", icon: Icon.trash(12), danger: true, onClick: async () => {
                      const ok = await confirmDialog({ title: "고객 삭제", desc: `${c.name}님을 소프트 삭제합니다.`, ok: "삭제", danger: true });
                      if (ok) toast(`${c.name}님을 삭제 처리했습니다`, "success");
                    }},
                  ]}
                />
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

export default Customers;
