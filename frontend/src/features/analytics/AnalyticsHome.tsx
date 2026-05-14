import { useState } from "react";
import { PageHeader } from "@/components/common";
import CampaignFunnel from "./CampaignFunnel";
import SegmentChannelMatrix from "./SegmentChannelMatrix";

type TabKey = "funnel" | "matrix";

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: "funnel",  label: "캠페인 단계 펀널" },
  { key: "matrix",  label: "세그먼트 × 채널 매트릭스" },
];

export default function AnalyticsHome() {
  const [activeTab, setActiveTab] = useState<TabKey>("funnel");

  return (
    <div>
      <PageHeader
        title="분석"
        subtitle="캠페인 단계 펀널 · 세그먼트 × 채널 매트릭스"
      />

      {/* 탭 바 */}
      <div
        role="tablist"
        style={{
          display: "flex",
          borderBottom: "2px solid #e2e8f0",
          marginBottom: 24,
          gap: 0,
        }}
      >
        {TABS.map((tab) => {
          const active = activeTab === tab.key;
          return (
            <button
              key={tab.key}
              role="tab"
              aria-selected={active}
              onClick={() => setActiveTab(tab.key)}
              style={{
                padding: "10px 20px",
                fontSize: 14,
                fontWeight: active ? 600 : 400,
                color: active ? "#3b5bdb" : "#64748b",
                background: "transparent",
                border: "none",
                borderBottom: active ? "2px solid #3b5bdb" : "2px solid transparent",
                marginBottom: -2,
                cursor: "pointer",
                fontFamily: "inherit",
                transition: "color 120ms, border-color 120ms",
                whiteSpace: "nowrap",
              }}
            >
              {tab.label}
            </button>
          );
        })}

      </div>

      {/* 탭 콘텐츠 */}
      {activeTab === "funnel"  && <CampaignFunnel embedded />}
      {activeTab === "matrix"  && <SegmentChannelMatrix embedded />}
    </div>
  );
}
