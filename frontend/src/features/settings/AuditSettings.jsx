import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function AuditSettings() {
  const logs = [
    { t: "2026-04-24 14:32", actor: "박지우", action: "캠페인 생성", target: "여름 프리오더 사전 안내", kind: "info" },
    { t: "2026-04-24 11:08", actor: "김나연", action: "FCM Private Key 조회", target: "studio-sel-prod", kind: "warn" },
    { t: "2026-04-23 18:40", actor: "이현우", action: "세그먼트 수정", target: "VIP · 최근 60일 재구매 없음", kind: "info" },
    { t: "2026-04-23 14:00", actor: "system", action: "캠페인 발송 완료", target: "장바구니 이탈 리타게팅 · 4월", kind: "info" },
    { t: "2026-04-22 10:22", actor: "박지우", action: "멤버 권한 변경", target: "최다은 Viewer → Editor", kind: "warn" },
    { t: "2026-04-21 09:14", actor: "김나연", action: "API 키 재발급", target: "Public API 키", kind: "warn" },
  ];
  return (
    <Card title="감사 로그" subtitle="모든 민감 정보 접근 및 중요 작업이 기록됩니다.">
      <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
        <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr 1.2fr 2fr", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
          <div>시각</div><div>작업자</div><div>작업</div><div>대상</div>
        </div>
        {logs.map((l, i) => (
          <div key={i} style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr 1.2fr 2fr", padding: "12px 16px", alignItems: "center", fontSize: 12, borderBottom: i < logs.length - 1 ? "1px solid #f1f5f9" : "none" }}>
            <div style={{ color: "#64748b", fontFamily: "var(--font-mono)" }}>{l.t}</div>
            <div style={{ color: "#1f2937" }}>{l.actor}</div>
            <div><Tag tone={l.kind === "warn" ? "warning" : "soft"}>{l.action}</Tag></div>
            <div style={{ color: "#64748b" }}>{l.target}</div>
          </div>
        ))}
      </div>
    </Card>
  );
}

export default AuditSettings;
