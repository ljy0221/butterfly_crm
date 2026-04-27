import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

const EVENT_CATS = ["purchase", "add_to_cart", "page_view", "login", "signup", "product_view", "wishlist_add", "checkout_start"];
const OPS = ["=", "≠", ">", "<", "≥", "≤", "contains", "in"];

function SegmentBuilder({ initial, onBack }) {
  const [name, setName] = React.useState(initial?.name || "VIP · 최근 60일 재구매 없음");
  const [desc, setDesc] = React.useState(initial?.desc || "누적 결제 50만원 이상이며, 지난 60일간 구매 기록이 없는 사용자");
  const [combiner, setCombiner] = React.useState("AND");
  const [groups, setGroups] = React.useState([
    {
      id: 1, combiner: "AND", rules: [
        { id: 11, kind: "event", event: "purchase", op: "≥", value: "1", period: "절대", range: "최근 365일", aggOp: "≥", agg: "3" },
        { id: 12, kind: "attribute", key: "purchase.total_amount", op: "≥", value: "500000", period: "누적" },
      ],
    },
    {
      id: 2, combiner: "AND", rules: [
        { id: 21, kind: "event", event: "purchase", op: "=", value: "0", period: "상대", range: "최근 60일", aggOp: "=", agg: "0" },
      ],
    },
  ]);
  const [previewing, setPreviewing] = React.useState(false);
  const [reach, setReach] = React.useState(2184);

  const addRule = (gid) => {
    setGroups(groups.map(g => g.id !== gid ? g : ({ ...g, rules: [...g.rules, { id: Date.now(), kind: "event", event: "add_to_cart", op: "≥", value: "1", period: "상대", range: "최근 30일", aggOp: "≥", agg: "1" }] })));
  };
  const removeRule = (gid, rid) => {
    setGroups(groups.map(g => g.id !== gid ? g : ({ ...g, rules: g.rules.filter(r => r.id !== rid) })));
  };
  const addGroup = () => {
    setGroups([...groups, { id: Date.now(), combiner: "AND", rules: [{ id: Date.now()+1, kind: "event", event: "page_view", op: "≥", value: "1", period: "상대", range: "최근 7일", aggOp: "≥", agg: "1" }] }]);
  };
  const runPreview = () => {
    setPreviewing(true);
    setTimeout(() => { setReach(Math.floor(1800 + Math.random() * 800)); setPreviewing(false); }, 900);
  };

  return (
    <div>
      <PageHeader
        back="세그먼트" onBack={onBack}
        title={initial ? "세그먼트 편집" : "새 세그먼트"}
        subtitle="조건을 조합해 실시간으로 예상 모수를 확인합니다."
        actions={<>
          <Button variant="ghost" size="md">초안 저장</Button>
          <Button variant="secondary" size="md" onClick={runPreview}>미리보기</Button>
          <Button variant="primaryStrong" size="md" icon={Icon.check(12)}>세그먼트 저장</Button>
        </>}
      />

      <div style={{ display: "grid", gridTemplateColumns: "1fr 360px", gap: 14, alignItems: "flex-start" }}>
        {/* LEFT — Builder */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* Basic info */}
          <Card>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 600, color: "#64748b", marginBottom: 6, letterSpacing: "-0.005em" }}>세그먼트 이름 <span style={{ color: "#ef4444" }}>*</span></div>
                <input value={name} onChange={e => setName(e.target.value)} style={{ width: "100%", height: 40, padding: "0 14px", fontFamily: "inherit", fontSize: 15, fontWeight: 600, color: "#1f2937", background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, outline: "none", boxSizing: "border-box" }} />
                <div style={{ marginTop: 6, fontSize: 11, color: "#94a3b8" }}>최대 30자 · 프로젝트 내 중복 불가</div>
              </div>
              <div>
                <div style={{ fontSize: 11, fontWeight: 600, color: "#64748b", marginBottom: 6 }}>설명</div>
                <input value={desc} onChange={e => setDesc(e.target.value)} placeholder="세그먼트가 대상으로 하는 사용자를 설명하세요" style={{ width: "100%", height: 40, padding: "0 14px", fontFamily: "inherit", fontSize: 13, color: "#1f2937", background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, outline: "none", boxSizing: "border-box" }} />
                <div style={{ marginTop: 6, fontSize: 11, color: "#94a3b8" }}>{desc.length} / 100</div>
              </div>
            </div>
          </Card>

          {/* Condition builder */}
          <Card>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)" }}>조건 구성</div>
                <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2 }}>최소 1개 이상의 조건이 필요합니다.</div>
              </div>
              <div style={{ display: "flex", gap: 4, padding: 3, background: "#f1f5f9", borderRadius: 7 }}>
                {["AND","OR"].map(c => (
                  <button key={c} onClick={() => setCombiner(c)} style={{
                    padding: "5px 14px", fontSize: 11, fontWeight: 700, border: 0, borderRadius: 5,
                    background: combiner === c ? "#fff" : "transparent", color: combiner === c ? "#1f2937" : "#64748b",
                    cursor: "pointer", fontFamily: "inherit",
                    boxShadow: combiner === c ? "0 1px 2px rgba(0,0,0,.06)" : "none",
                  }}>{c}</button>
                ))}
              </div>
            </div>

            {/* Groups */}
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {groups.map((g, gi) => (
                <React.Fragment key={g.id}>
                  <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, background: "#fafbfe", padding: 14 }}>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <Tag tone="info">그룹 {gi+1}</Tag>
                        <span style={{ fontSize: 11, color: "#64748b" }}>내부 조건은 <b>{g.combiner}</b>로 결합됩니다.</span>
                      </div>
                      <button onClick={() => setGroups(groups.filter(x => x.id !== g.id))} style={{ border: 0, background: "transparent", color: "#94a3b8", cursor: "pointer", fontSize: 11 }}>그룹 삭제</button>
                    </div>

                    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                      {g.rules.map((r, ri) => (
                        <div key={r.id}>
                          {ri > 0 && <div style={{ fontSize: 10, color: "#64748b", fontWeight: 700, padding: "2px 0 6px 8px" }}>{g.combiner}</div>}
                          <RuleRow rule={r} onRemove={() => removeRule(g.id, r.id)} />
                        </div>
                      ))}
                    </div>

                    <button onClick={() => addRule(g.id)} style={{
                      marginTop: 10, height: 32, padding: "0 12px", fontSize: 12, fontWeight: 500,
                      border: "1px dashed #cbd5e1", borderRadius: 8, background: "#fff", color: "#64748b",
                      cursor: "pointer", fontFamily: "inherit", display: "inline-flex", alignItems: "center", gap: 6,
                    }}>{Icon.plus(12)} 조건 추가</button>
                  </div>

                  {gi < groups.length - 1 && (
                    <div style={{ display: "flex", alignItems: "center", gap: 10, paddingLeft: 6 }}>
                      <div style={{ flex: 1, height: 1, background: "#e2e8f0" }} />
                      <Tag tone="dark">{combiner}</Tag>
                      <div style={{ flex: 1, height: 1, background: "#e2e8f0" }} />
                    </div>
                  )}
                </React.Fragment>
              ))}

              <button onClick={addGroup} style={{
                height: 40, border: "1px dashed #cbd5e1", borderRadius: 10, background: "#fff", color: "#3b5bdb",
                cursor: "pointer", fontFamily: "inherit", fontWeight: 600, fontSize: 12,
                display: "inline-flex", alignItems: "center", justifyContent: "center", gap: 6,
              }}>{Icon.plus(12)} 조건 그룹 추가</button>
            </div>
          </Card>

          {/* User attribute filter */}
          <Card title="사용자 속성 필터" subtitle="선택 사항 · 생년월일·가입 시점·등급 등" action={<Button variant="text" size="sm" icon={Icon.plus(12)}>속성 추가</Button>}>
            <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              <Tag tone="soft">등급 = VIP</Tag>
              <Tag tone="soft">가입일 ≥ 2024-01-01</Tag>
              <Tag tone="soft">연령대 = 30대 OR 40대</Tag>
            </div>
          </Card>
        </div>

        {/* RIGHT — Preview panel */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14, position: "sticky", top: 0 }}>
          <Card>
            <div style={{ fontSize: 11, fontWeight: 600, color: "#64748b", letterSpacing: "0.04em", textTransform: "uppercase" }}>예상 모수</div>
            <div style={{ marginTop: 10, display: "flex", alignItems: "baseline", gap: 8 }}>
              <div style={{ fontSize: 34, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)", fontVariantNumeric: "tabular-nums", letterSpacing: "-0.02em", lineHeight: 1 }}>
                {previewing ? <span style={{ color: "#94a3b8" }}>계산중…</span> : fmtNum(reach)}
              </div>
              <div style={{ fontSize: 12, color: "#64748b" }}>명</div>
            </div>
            <div style={{ marginTop: 4, fontSize: 11, color: "#94a3b8" }}>전체 고객의 <b style={{ color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>{(reach/284911*100).toFixed(2)}%</b> · HyperLogLog 추정</div>

            {/* Distribution bar */}
            <div style={{ marginTop: 18, height: 8, background: "#f1f5f9", borderRadius: 4, position: "relative", overflow: "hidden" }}>
              <div style={{ height: "100%", width: `${(reach/284911*100).toFixed(2)}%`, background: "linear-gradient(90deg, #3b5bdb, #637fff)", borderRadius: 4 }} />
            </div>

            <Button variant="secondary" size="sm" style={{ marginTop: 14, width: "100%", justifyContent: "center" }} onClick={runPreview}>
              다시 계산하기
            </Button>
            <div style={{ marginTop: 10, fontSize: 10, color: "#94a3b8", textAlign: "center" }}>최종 계산 · 방금 전</div>
          </Card>

          <Card title="샘플 대상자" subtitle="조건에 매칭되는 사용자 5명" action={<span style={{ fontSize: 11, color: "#2563eb", fontWeight: 600, cursor: "pointer" }}>전체 보기</span>}>
            {["이서연","최유진","문채아","오지훈","한도윤"].map((n, i) => (
              <div key={n} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 0", borderBottom: i < 4 ? "1px dashed #f1f5f9" : "none" }}>
                <Avatar name={n} size={26} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 12, color: "#1f2937", fontWeight: 500 }}>{n}</div>
                  <div style={{ fontSize: 11, color: "#94a3b8" }}>최근 활동 {["12분","34분","2시간","5시간","1일"][i]} 전 · 이벤트 {[248,89,412,62,142][i]}</div>
                </div>
              </div>
            ))}
          </Card>

          <Card title="연결된 캠페인" subtitle="2개">
            {["VIP 감사 쿠폰 안내","여름 프리오더 사전 안내"].map(c => (
              <div key={c} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 0", fontSize: 12 }}>
                <span style={{ width: 6, height: 6, borderRadius: 999, background: "#16a34a" }} />
                <span style={{ flex: 1, color: "#1f2937" }}>{c}</span>
                <Tag tone="success" dot>운영 중</Tag>
              </div>
            ))}
          </Card>
        </div>
      </div>
    </div>
  );
}

function RuleRow({ rule, onRemove }) {
  if (rule.kind === "attribute") {
    return (
      <div style={{ display: "flex", alignItems: "center", gap: 8, background: "#fff", padding: 10, border: "1px solid #e2e8f0", borderRadius: 8 }}>
        <Tag tone="soft">속성</Tag>
        <div style={{ display: "flex", alignItems: "center", gap: 6, padding: "0 10px", height: 30, border: "1px solid #e2e8f0", borderRadius: 6, background: "#f8fafc", fontFamily: "var(--font-mono)", fontSize: 12, color: "#1f2937" }}>
          {rule.key}
        </div>
        <select defaultValue={rule.op} style={selectStyle}>
          {OPS.map(o => <option key={o}>{o}</option>)}
        </select>
        <div style={{ padding: "0 10px", height: 30, border: "1px solid #e2e8f0", borderRadius: 6, background: "#fff", fontSize: 12, color: "#1f2937", display: "flex", alignItems: "center", fontVariantNumeric: "tabular-nums" }}>{rule.value}</div>
        <div style={{ flex: 1 }} />
        <button onClick={onRemove} style={{ border: 0, background: "transparent", color: "#94a3b8", cursor: "pointer", display: "flex" }}>{Icon.x(14)}</button>
      </div>
    );
  }
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, background: "#fff", padding: 10, border: "1px solid #e2e8f0", borderRadius: 8, flexWrap: "wrap" }}>
      <Tag tone="info">이벤트</Tag>
      <select defaultValue={rule.event} style={{ ...selectStyle, fontFamily: "var(--font-mono)" }}>
        {EVENT_CATS.map(c => <option key={c}>{c}</option>)}
      </select>
      <span style={{ fontSize: 11, color: "#64748b" }}>가</span>
      <select defaultValue={rule.aggOp} style={selectStyle}>
        {OPS.map(o => <option key={o}>{o}</option>)}
      </select>
      <div style={{ padding: "0 10px", height: 30, border: "1px solid #e2e8f0", borderRadius: 6, background: "#fff", fontSize: 12, color: "#1f2937", display: "flex", alignItems: "center", fontVariantNumeric: "tabular-nums", width: 48 }}>{rule.agg}</div>
      <span style={{ fontSize: 11, color: "#64748b" }}>회 발생</span>
      <span style={{ fontSize: 11, color: "#cbd5e1" }}>·</span>
      <select defaultValue={rule.range} style={selectStyle}>
        <option>최근 7일</option><option>최근 30일</option><option>최근 60일</option><option>최근 90일</option><option>최근 365일</option>
      </select>
      <div style={{ flex: 1 }} />
      <button onClick={onRemove} style={{ border: 0, background: "transparent", color: "#94a3b8", cursor: "pointer", display: "flex" }}>{Icon.x(14)}</button>
    </div>
  );
}

const selectStyle = {
  height: 30, padding: "0 28px 0 10px", border: "1px solid #e2e8f0", borderRadius: 6,
  background: "#fff", fontFamily: "inherit", fontSize: 12, color: "#1f2937",
  outline: "none", appearance: "none",
  backgroundImage: 'url("data:image/svg+xml;utf8,<svg xmlns=\'http://www.w3.org/2000/svg\' width=\'10\' height=\'10\' viewBox=\'0 0 24 24\' fill=\'none\' stroke=\'%2394a3b8\' stroke-width=\'2\' stroke-linecap=\'round\' stroke-linejoin=\'round\'><polyline points=\'6 9 12 15 18 9\'></polyline></svg>")',
  backgroundRepeat: "no-repeat", backgroundPosition: "right 8px center",
  cursor: "pointer",
};


export default SegmentBuilder;
