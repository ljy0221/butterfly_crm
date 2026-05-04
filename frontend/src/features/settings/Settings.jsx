import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";
import ProjectSettings from "./ProjectSettings.jsx";
import KeySettings from "./KeySettings.jsx";
import FcmSettings from "./FcmSettings.jsx";
import MemberSettings from "./MemberSettings.jsx";
import AuditSettings from "./AuditSettings.jsx";

function Settings() {
  const [tab, setTab] = React.useState("project");
  const tabs = [
    { k: "project", l: "프로젝트" },
    { k: "keys", l: "API 키" },
    { k: "fcm", l: "FCM 연동" },
    { k: "members", l: "멤버 (4)" },
    { k: "audit", l: "감사 로그" },
  ];
  return (
    <div>
      <PageHeader title="설정" subtitle="프로젝트·API 키·FCM·멤버를 관리합니다." />

      <div style={{ display: "flex", gap: 2, marginBottom: 20, borderBottom: "1px solid #e2e8f0" }}>
        {tabs.map(t => (
          <button key={t.k} onClick={() => setTab(t.k)} style={{
            padding: "10px 16px", fontSize: 13, fontWeight: 500, border: 0, background: "transparent",
            color: tab === t.k ? "#1f2937" : "#64748b",
            borderBottom: tab === t.k ? "2px solid #3b5bdb" : "2px solid transparent",
            marginBottom: -1, cursor: "pointer", fontFamily: "inherit",
          }}>{t.l}</button>
        ))}
      </div>

      {tab === "project" && <ProjectSettings />}
      {tab === "keys" && <KeySettings />}
      {tab === "fcm" && <FcmSettings />}
      {tab === "members" && <MemberSettings />}
      {tab === "audit" && <AuditSettings />}
    </div>
  );
}

export default Settings;
