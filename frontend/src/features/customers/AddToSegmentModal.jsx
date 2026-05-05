import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function AddToSegmentModal({ close, count, onDone }) {
  const { SEGMENTS } = CRM_DATA;
  const [picked, setPicked] = React.useState(null);
  const submit = () => {
    if (!picked) { toast("세그먼트를 선택해주세요", "error"); return; }
    close(); onDone && onDone();
    toast(`${count}명을 '${picked.name}' 세그먼트에 추가했습니다`, "success");
  };
  return (
    <div style={{ width: 480, background: "#fff", borderRadius: 14, boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)", overflow: "hidden" }}>
      <div style={{ padding: "18px 22px", borderBottom: "1px solid #f1f5f9" }}>
        <div style={{ fontSize: 16, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)" }}>세그먼트에 추가</div>
        <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>선택한 고객 {count}명을 정적 세그먼트에 포함시킵니다.</div>
      </div>
      <div style={{ padding: "12px 0", maxHeight: 400, overflowY: "auto" }}>
        {SEGMENTS.slice(0, 6).map(s => (
          <div key={s.id} onClick={() => setPicked(s)} style={{
            padding: "10px 22px", cursor: "pointer", display: "flex", alignItems: "center", gap: 12,
            background: picked?.id === s.id ? "#eff3fe" : "transparent",
            borderLeft: picked?.id === s.id ? "3px solid #3b5bdb" : "3px solid transparent",
          }}>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{s.name}</div>
              <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>{s.desc}</div>
            </div>
            <span style={{ fontSize: 11, color: "#64748b", fontVariantNumeric: "tabular-nums" }}>{fmtNum(s.size)}명</span>
          </div>
        ))}
      </div>
      <div style={{ padding: "12px 22px", borderTop: "1px solid #f1f5f9", display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <Button variant="ghost" size="md" onClick={close}>취소</Button>
        <Button variant="primaryStrong" size="md" onClick={submit}>추가</Button>
      </div>
    </div>
  );
}

export default AddToSegmentModal;
