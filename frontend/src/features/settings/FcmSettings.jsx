import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function FcmSettings() {
  return (
    <Card title="FCM 연동" subtitle="Firebase 프로젝트의 Private Key를 등록해 푸시 발송을 시작합니다.">
      <div style={{ display: "flex", alignItems: "center", gap: 12, padding: 14, background: "rgba(22,163,74,.08)", borderRadius: 8, marginBottom: 16 }}>
        <span style={{ color: "#16a34a", display: "flex" }}>{Icon.check(16)}</span>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: "#15803d" }}>연동 완료</div>
          <div style={{ fontSize: 11, color: "#166534" }}>프로젝트 ID: <span style={{ fontFamily: "var(--font-mono)" }}>studio-sel-prod</span> · 2026-03-04 등록</div>
        </div>
        <Button variant="ghost" size="sm">재등록</Button>
      </div>
      <div style={{ padding: 14, background: "#f8fafc", borderRadius: 8, fontSize: 12, color: "#64748b", lineHeight: 1.7 }}>
        <div style={{ fontWeight: 600, color: "#1f2937", marginBottom: 4 }}>✓ Private Key는 AES-256로 암호화되어 저장됩니다.</div>
        키 파일은 Firebase Console → 프로젝트 설정 → 서비스 계정에서 발급받을 수 있습니다. 키 접근 이력은 감사 로그에 전수 기록됩니다.
      </div>
    </Card>
  );
}

export default FcmSettings;
