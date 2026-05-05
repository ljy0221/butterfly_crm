import React from "react";
import {
  Icon, Button, Tag, ChannelBadge, CampaignStatus, Card, StatCard,
  Input, Select, Avatar, Checkbox, Menu, SearchInput, PageHeader,
} from "../../components/common";
import { toast, openModal, confirmDialog } from "../../lib/ui-bus";
import { fmtNum } from "../../utils/fmt.js";
import { CRM_DATA } from "../../data/mock.js";

function ProjectSettings() {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 14 }}>
      <Card title="프로젝트 정보">
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <Input label="회사명" defaultValue="스튜디오 셀 주식회사" />
          <Input label="프로젝트명" defaultValue="스튜디오 셀 · 운영 프로젝트" />
          <Input label="사업자 번호" defaultValue="204-81-48291" />
          <Select label="플랜"><option>Growth · ~월 500만건</option><option>Business · ~월 2천만건</option></Select>
        </div>
      </Card>
      <Card title="현재 사용량" subtitle="이번 달">
        <div style={{ marginBottom: 14 }}>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 6 }}>
            <span>이벤트 수집</span><span style={{ color: "#64748b", fontVariantNumeric: "tabular-nums" }}>8.8M / 50M</span>
          </div>
          <div style={{ height: 6, background: "#f1f5f9", borderRadius: 3 }}>
            <div style={{ height: "100%", width: "18%", background: "#3b5bdb", borderRadius: 3 }} />
          </div>
        </div>
        <div>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 6 }}>
            <span>캠페인 발송</span><span style={{ color: "#64748b", fontVariantNumeric: "tabular-nums" }}>542K / 5M</span>
          </div>
          <div style={{ height: 6, background: "#f1f5f9", borderRadius: 3 }}>
            <div style={{ height: "100%", width: "11%", background: "#3b5bdb", borderRadius: 3 }} />
          </div>
        </div>
      </Card>
    </div>
  );
}

export default ProjectSettings;
