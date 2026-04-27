import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function AddEventCategoryModal({ close }) {
  const [name, setName] = React.useState("");
  const [desc, setDesc] = React.useState("");
  const [props, setProps] = React.useState([{ key: "", type: "string", required: false }]);
  const valid = /^[a-z_][a-z0-9_]{2,39}$/.test(name);
  const addProp = () => setProps(p => [...p, { key: "", type: "string", required: false }]);
  const rmProp = (i) => setProps(p => p.filter((_, j) => j !== i));
  const updateProp = (i, patch) => setProps(p => p.map((x, j) => j === i ? { ...x, ...patch } : x));
  const submit = () => {
    if (!valid) { toast("이벤트명은 영소문자·숫자·언더스코어로 3~40자여야 합니다", "error"); return; }
    close();
    toast(`이벤트 '${name}' 카테고리를 추가했습니다`, "success");
  };
  return (
    <div style={{ width: 560, background: "#fff", borderRadius: 14, boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)", overflow: "hidden" }}>
      <div style={{ padding: "18px 22px", borderBottom: "1px solid #f1f5f9", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <div style={{ fontSize: 17, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)" }}>이벤트 카테고리 추가</div>
          <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>등록된 이벤트만 수집할 수 있습니다.</div>
        </div>
        <span onClick={close} style={{ color: "#94a3b8", cursor: "pointer" }}>{Icon.x(16)}</span>
      </div>
      <div style={{ padding: 22, display: "flex", flexDirection: "column", gap: 14 }}>
        <Input label="이벤트명" placeholder="예: wishlist_added" value={name} onChange={e => setName(e.target.value)}
          help={name && !valid ? "영소문자·숫자·언더스코어 3~40자" : "수집 시 이 이름을 사용합니다"} error={name && !valid} />
        <Input label="설명 (선택)" placeholder="이 이벤트가 언제 발생하는지" value={desc} onChange={e => setDesc(e.target.value)} />
        <div>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 6 }}>
            <div style={{ fontSize: 12, fontWeight: 500, color: "#334155" }}>속성 스키마</div>
            <span onClick={addProp} style={{ fontSize: 11, color: "#3b5bdb", cursor: "pointer", fontWeight: 600 }}>+ 속성 추가</span>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {props.map((p, i) => (
              <div key={i} style={{ display: "grid", gridTemplateColumns: "1.4fr 0.8fr 0.5fr 30px", gap: 6 }}>
                <input value={p.key} onChange={e => updateProp(i, { key: e.target.value })} placeholder="속성 이름" style={{ height: 34, padding: "0 12px", fontFamily: "var(--font-mono)", fontSize: 12, border: "1px solid #e2e8f0", borderRadius: 8, outline: 0 }} />
                <select value={p.type} onChange={e => updateProp(i, { type: e.target.value })} style={{ height: 34, padding: "0 10px", fontSize: 12, border: "1px solid #e2e8f0", borderRadius: 8, background: "#fff", fontFamily: "inherit" }}>
                  <option>string</option><option>number</option><option>boolean</option><option>datetime</option>
                </select>
                <label style={{ display: "flex", alignItems: "center", justifyContent: "center" }}><Checkbox checked={p.required} onChange={v => updateProp(i, { required: v })} label="필수" /></label>
                <button onClick={() => rmProp(i)} style={{ border: 0, background: "transparent", color: "#cbd5e1", cursor: "pointer" }}>{Icon.trash(13)}</button>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div style={{ padding: "12px 22px", borderTop: "1px solid #f1f5f9", display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <Button variant="ghost" size="md" onClick={close}>취소</Button>
        <Button variant="primaryStrong" size="md" icon={Icon.check(12)} onClick={submit}>이벤트 생성</Button>
      </div>
    </div>
  );
}

export default AddEventCategoryModal;
