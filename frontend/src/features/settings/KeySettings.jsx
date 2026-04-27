import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus.js";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function KeySettings() {
  return (
    <Card title="API 키" subtitle="이벤트 수집 및 외부 연동에 사용됩니다." action={<Button variant="primaryStrong" size="sm" icon={Icon.plus(12)}>재발급</Button>}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {[
          { label: "Public API 키", desc: "이벤트 수집용. 클라이언트에 노출 가능.", val: "pk_live_sel_a82f41•••••••••", badge: "활성" },
          { label: "Secret API 키", desc: "서버 간 연동용. 절대 노출 금지.", val: "sk_live_sel_•••••••••••••••••••••", badge: "비공개" },
        ].map(k => (
          <div key={k.label} style={{ border: "1px solid #e2e8f0", borderRadius: 10, padding: 16, display: "flex", alignItems: "center", gap: 16 }}>
            <div style={{ flex: 1 }}>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: "#1f2937" }}>{k.label}</span>
                <Tag tone={k.badge === "활성" ? "success" : "soft"} dot>{k.badge}</Tag>
              </div>
              <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>{k.desc}</div>
              <div style={{ marginTop: 10, padding: "8px 12px", background: "#0f172a", color: "#e2e8f0", borderRadius: 6, fontFamily: "var(--font-mono)", fontSize: 12, display: "flex", alignItems: "center", gap: 10 }}>
                <span style={{ flex: 1 }}>{k.val}</span>
                <span style={{ cursor: "pointer", color: "#94a3b8" }}>{Icon.copy(13)}</span>
              </div>
            </div>
            <Button variant="ghost" size="sm">재발급</Button>
          </div>
        ))}
      </div>
    </Card>
  );
}

export default KeySettings;
