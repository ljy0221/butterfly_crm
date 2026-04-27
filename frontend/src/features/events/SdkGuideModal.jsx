import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function SdkGuideModal({ close }) {
  const [tab, setTab] = React.useState("javascript");
  const snippets = {
    javascript: `import Butterfly from '@butterfly/web-sdk';\n\nButterfly.init({\n  publicKey: 'pk_live_sel_a82f41•••',\n});\n\nButterfly.track('purchase', {\n  order_id: 'SO-2026-041829',\n  total_amount: 184000,\n  is_first_order: true,\n});`,
    android: `// build.gradle\nimplementation 'com.butterfly.crm:sdk-android:1.4.2'\n\n// Application.kt\nButterfly.init(this, "pk_live_sel_a82f41•••")\nButterfly.track("purchase", mapOf(\n    "order_id" to "SO-2026-041829",\n    "total_amount" to 184000\n))`,
    ios: `import ButterflySDK\n\nButterfly.initialize(publicKey: "pk_live_sel_a82f41•••")\nButterfly.track("purchase", properties: [\n    "order_id": "SO-2026-041829",\n    "total_amount": 184000\n])`,
  };
  return (
    <div style={{ width: 640, background: "#fff", borderRadius: 14, boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)", overflow: "hidden" }}>
      <div style={{ padding: "18px 22px", borderBottom: "1px solid #f1f5f9", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div>
          <div style={{ fontSize: 17, fontWeight: 700, color: "#1f2937", fontFamily: "var(--font-display)" }}>SDK 설치 가이드</div>
          <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>Public API 키를 사용해 이벤트를 수집하세요. Secret Key는 서버에만 사용합니다.</div>
        </div>
        <span onClick={close} style={{ color: "#94a3b8", cursor: "pointer" }}>{Icon.x(16)}</span>
      </div>
      <div style={{ padding: 22 }}>
        <div style={{ display: "flex", gap: 2, marginBottom: 14, borderBottom: "1px solid #e2e8f0" }}>
          {[["javascript","Web · JavaScript"],["android","Android"],["ios","iOS"]].map(([k, l]) => (
            <button key={k} onClick={() => setTab(k)} style={{
              padding: "8px 14px", fontSize: 12, fontWeight: 500, border: 0, background: "transparent",
              color: tab === k ? "#1f2937" : "#64748b", fontFamily: "inherit",
              borderBottom: tab === k ? "2px solid #3b5bdb" : "2px solid transparent",
              marginBottom: -1, cursor: "pointer",
            }}>{l}</button>
          ))}
        </div>
        <div style={{ position: "relative" }}>
          <pre style={{ margin: 0, padding: 16, background: "#0f172a", color: "#e2e8f0", borderRadius: 10, fontFamily: "var(--font-mono)", fontSize: 12, lineHeight: 1.6, overflow: "auto" }}>{snippets[tab]}</pre>
          <button onClick={() => { navigator.clipboard && navigator.clipboard.writeText(snippets[tab]); toast("코드를 복사했습니다", "success"); }}
            style={{ position: "absolute", top: 10, right: 10, height: 26, padding: "0 10px", fontSize: 11, fontWeight: 600, border: 0, borderRadius: 6, background: "rgba(255,255,255,.12)", color: "#fff", cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 4, fontFamily: "inherit" }}>{Icon.copy(11)} 복사</button>
        </div>
        <div style={{ marginTop: 14, padding: 12, background: "#f8fafc", borderRadius: 8, fontSize: 12, color: "#64748b", lineHeight: 1.7 }}>
          <div style={{ fontWeight: 600, color: "#1f2937", marginBottom: 2 }}>💡 이벤트 카테고리 먼저 생성해주세요</div>
          플랫폼에 등록되지 않은 이벤트는 수집되지 않습니다. 기본 제공: <code style={{ fontFamily: "var(--font-mono)", color: "#3b5bdb" }}>purchase · add_to_cart · page_view · login · signup</code>
        </div>
      </div>
      <div style={{ padding: "12px 22px", borderTop: "1px solid #f1f5f9", display: "flex", justifyContent: "flex-end", gap: 8 }}>
        <Button variant="ghost" size="md" onClick={close}>닫기</Button>
        <Button variant="primaryStrong" size="md" onClick={() => { close(); toast("상세 문서 페이지를 새 창에서 엽니다", "info"); }}>상세 문서 보기</Button>
      </div>
    </div>
  );
}

export default SdkGuideModal;
