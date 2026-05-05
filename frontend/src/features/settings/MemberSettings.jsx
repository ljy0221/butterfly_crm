import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function MemberSettings() {
  const members = [
    { name: "김나연", email: "nayeon.kim@studiosel.co.kr", role: "Owner", last: "방금" },
    { name: "박지우", email: "jiwoo.park@studiosel.co.kr", role: "Editor", last: "12분 전" },
    { name: "이현우", email: "hyunwoo.lee@studiosel.co.kr", role: "Editor", last: "2시간 전" },
    { name: "최다은", email: "daeun.choi@studiosel.co.kr", role: "Viewer", last: "어제" },
  ];
  const tone = r => r === "Owner" ? "warning" : r === "Editor" ? "info" : "default";
  return (
    <Card title="멤버" subtitle={`${members.length}명 · Owner 1명 · Editor 2명 · Viewer 1명`} action={<Button variant="text" size="sm">회사 인증 번호 보기</Button>}>
      <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
        <div style={{ display: "grid", gridTemplateColumns: "2fr 2fr 1fr 1fr 40px", padding: "12px 16px", background: "#f8fafc", borderBottom: "1px solid #e2e8f0", fontSize: 11, color: "#64748b", fontWeight: 600 }}>
          <div>이름</div><div>이메일</div><div>권한</div><div>최근 접속</div><div></div>
        </div>
        {members.map((m, i) => (
          <div key={m.email} style={{ display: "grid", gridTemplateColumns: "2fr 2fr 1fr 1fr 40px", padding: "12px 16px", alignItems: "center", fontSize: 13, borderBottom: i < members.length - 1 ? "1px solid #f1f5f9" : "none" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <Avatar name={m.name} size={28} />
              <span style={{ color: "#1f2937", fontWeight: 500 }}>{m.name}</span>
            </div>
            <div style={{ color: "#64748b", fontSize: 12 }}>{m.email}</div>
            <div><Tag tone={tone(m.role)}>{m.role}</Tag></div>
            <div style={{ color: "#64748b", fontSize: 12 }}>{m.last}</div>
            <div style={{ color: "#cbd5e1", textAlign: "right" }}>{Icon.dots(14)}</div>
          </div>
        ))}
      </div>
    </Card>
  );
}

export default MemberSettings;
