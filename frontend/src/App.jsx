import React from "react";

// Layout + global feedback hosts
import { Sidebar, Topbar } from "./components/layout";
import { ToastHost, ModalHost } from "./components/feedback";

// Tweaks panel (design-system preview harness)
import {
  TweaksPanel, TweakSection, TweakToggle,
} from "./components/tweaks/TweaksPanel.jsx";
import { useTweaks } from "./hooks/useTweaks.js";

// Data
import { CRM_DATA } from "./data/mock.js";

// Feature screens
import Dashboard from "./features/dashboard/Dashboard.jsx";
import Events from "./features/events/Events.jsx";
import EventDetail from "./features/events/EventDetail.jsx";
import Customers from "./features/customers/Customers.jsx";
import Segments from "./features/segments/Segments.jsx";
import SegmentBuilder from "./features/segments/SegmentBuilder.jsx";
import Campaigns from "./features/campaigns/Campaigns.jsx";
import CampaignWizard from "./features/campaigns/CampaignWizard.jsx";
import CampaignDetail from "./features/campaigns/CampaignDetail.jsx";
import Journey from "./features/journey/Journey.jsx";
import Templates from "./features/templates/Templates.jsx";
import Analytics from "./features/analytics/Analytics.jsx";
import Settings from "./features/settings/Settings.jsx";

const TWEAK_DEFAULTS = {
  sidebarCollapsed: false,
};

export default function App() {
  const [tweaks, setTweak] = useTweaks(TWEAK_DEFAULTS);
  const [route, setRoute] = React.useState("대시보드");

  // Route format: "primary", "primary-sub", or "primary-sub:param"
  const [primary, subroute] = route.split(":")[0].split("-");
  const param = route.split(":")[1];

  const crumbs = React.useMemo(() => {
    const sub = subroute
      ? subroute === "신규"
        ? "신규"
        : subroute === "상세"
          ? "상세"
          : subroute
      : null;
    return ["Studio SEL", primary].concat(sub ? [sub + (param ? " · " + param : "")] : []);
  }, [primary, subroute, param]);

  let screen;
  switch (primary) {
    case "대시보드":
      screen = <Dashboard onNavigate={setRoute} />;
      break;
    case "이벤트":
      if (subroute === "상세") {
        const ev =
          CRM_DATA.EVENT_CATEGORIES.find((e) => e.name === param) ||
          CRM_DATA.EVENT_CATEGORIES[0];
        screen = <EventDetail event={ev} onBack={() => setRoute("이벤트")} />;
      } else {
        screen = <Events onNavigate={setRoute} />;
      }
      break;
    case "고객":
      screen = <Customers />;
      break;
    case "세그먼트":
      if (subroute === "신규" || subroute === "상세") {
        screen = (
          <SegmentBuilder mode={subroute} name={param} onBack={() => setRoute("세그먼트")} />
        );
      } else {
        screen = <Segments onNavigate={setRoute} />;
      }
      break;
    case "캠페인":
      if (subroute === "신규") {
        screen = (
          <CampaignWizard
            onBack={() => setRoute("캠페인")}
            onDone={() => setRoute("캠페인")}
          />
        );
      } else if (subroute === "상세") {
        screen = <CampaignDetail id={param} onBack={() => setRoute("캠페인")} />;
      } else {
        screen = <Campaigns onNavigate={setRoute} />;
      }
      break;
    case "자동화":
      screen = <Journey />;
      break;
    case "템플릿":
      screen = <Templates />;
      break;
    case "분석":
      screen = <Analytics />;
      break;
    case "설정":
      screen = <Settings />;
      break;
    default:
      screen = <Dashboard onNavigate={setRoute} />;
  }

  return (
    <div style={{ display: "flex", height: "100vh", background: "#f5f7fb", overflow: "hidden" }}>
      <Sidebar
        active={primary}
        onNavigate={setRoute}
        collapsed={tweaks.sidebarCollapsed}
      />
      <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <Topbar
          crumbs={crumbs}
          onToggleSidebar={() => setTweak("sidebarCollapsed", !tweaks.sidebarCollapsed)}
          onNavigate={setRoute}
        />
        <main
          data-screen-label={primary}
          style={{ flex: 1, padding: "24px 32px 60px", overflow: "auto" }}
        >
          {screen}
        </main>
      </div>

      <TweaksPanel title="Tweaks">
        <TweakSection label="Layout" />
        <TweakToggle
          label="사이드바 축소"
          value={tweaks.sidebarCollapsed}
          onChange={(v) => setTweak("sidebarCollapsed", v)}
        />
        <TweakSection label="빠른 이동" />
        <div style={{ display: "flex", flexDirection: "column", gap: 6, marginTop: 4 }}>
          {[
            { k: "세그먼트-신규", l: "→ 세그먼트 빌더" },
            { k: "캠페인-신규", l: "→ 캠페인 위저드" },
            { k: "이벤트-상세:purchase", l: "→ 이벤트 스키마" },
            { k: "캠페인-상세:cmp_4821", l: "→ 캠페인 상세" },
          ].map((r) => (
            <button
              key={r.k}
              onClick={() => setRoute(r.k)}
              style={{
                textAlign: "left",
                padding: "6px 10px",
                fontSize: 12,
                fontFamily: "inherit",
                border: "1px solid #e2e8f0",
                background: "#fff",
                borderRadius: 6,
                color: "#334155",
                cursor: "pointer",
              }}
            >
              {r.l}
            </button>
          ))}
        </div>
      </TweaksPanel>
      <ToastHost />
      <ModalHost />
    </div>
  );
}
