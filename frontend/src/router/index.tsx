import { createBrowserRouter, Navigate } from "react-router-dom";
import AppLayout from "./AppLayout";
import AuthLayout from "./AuthLayout";
import { RequireAuth, RequireProject, RequireAdmin } from "./guards";
import Placeholder from "@/pages/Placeholder";
import NotFound from "@/pages/NotFound";
import ProjectSelect from "@/pages/ProjectSelect";
import LoginPage from "@/features/auth/LoginPage";
import SignupPage from "@/features/auth/SignupPage";
import OwnerPasswordSetup from "@/features/auth/OwnerPasswordSetup";
import ApplyPage from "@/features/application/ApplyPage";
import AdminApplications from "@/features/admin/AdminApplications";
import SettingsLayout from "@/features/settings/SettingsLayout";
import Dashboard from "@/features/dashboard/Dashboard";
import Templates from "@/features/templates/Templates";
import Segments from "@/features/segments/Segments";
import SegmentBuilder from "@/features/segments/SegmentBuilder";
import Campaigns from "@/features/campaigns/Campaigns";
import CampaignWizard from "@/features/campaigns/CampaignWizard";
import CampaignDetail from "@/features/campaigns/CampaignDetail";
import Events from "@/features/events/Events";
import EventDetail from "@/features/events/EventDetail";
import AdminAuditLogs from "@/features/audit/AdminAuditLogs";
import AnalyticsHome from "@/features/analytics/AnalyticsHome";
import SegmentChannelMatrix from "@/features/analytics/SegmentChannelMatrix";
import CampaignFunnel from "@/features/analytics/CampaignFunnel";
import DemoLogin from "@/pages/DemoLogin";
import UserProfile from "@/features/users/UserProfile";

/**
 * Sprint 1 진행 중 — auth/application 도메인 element 실제 페이지로 교체 완료.
 * 나머지 도메인은 placeholder 유지(스프린트 진행에 따라 교체).
 */
export const router = createBrowserRouter([
  {
    path: "/",
    element: <Navigate to="/login" replace />,
  },
  {
    element: <AuthLayout />,
    children: [
      {
        path: "/login",
        element: <LoginPage />,
      },
      {
        path: "/signup",
        element: <SignupPage />,
      },
      {
        path: "/apply",
        element: <ApplyPage />,
      },
      {
        path: "/owner/setup",
        element: <OwnerPasswordSetup />,
      },
      ...(import.meta.env.DEV
        ? [
            {
              path: "/demo",
              element: <DemoLogin />,
            },
          ]
        : []),
    ],
  },
  {
    path: "/projects",
    element: (
      <RequireAuth>
        <ProjectSelect />
      </RequireAuth>
    ),
  },
  {
    path: "/admin",
    element: (
      <RequireAuth>
        <RequireAdmin>
          <AppLayout />
        </RequireAdmin>
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="applications" replace /> },
      {
        path: "applications",
        element: <AdminApplications />,
      },
      {
        path: "audit",
        element: <AdminAuditLogs />,
      },
    ],
  },
  {
    path: "/p/:projectId",
    element: (
      <RequireAuth>
        <RequireProject>
          <AppLayout />
        </RequireProject>
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      {
        path: "dashboard",
        element: <Dashboard />,
      },
      {
        path: "events",
        element: <Events />,
      },
      {
        path: "events/:eventName",
        element: <EventDetail />,
      },
      {
        path: "customers",
        element: <Placeholder title="고객" domain="customer" sprint="Sprint 7" />,
      },
      {
        path: "users/:userId",
        element: <UserProfile />,
      },
      {
        path: "segments",
        element: <Segments />,
      },
      {
        path: "segments/new",
        element: <SegmentBuilder mode="create" />,
      },
      {
        path: "segments/:segmentId",
        element: <SegmentBuilder mode="view" />,
      },
      {
        path: "campaigns",
        element: <Campaigns />,
      },
      {
        path: "campaigns/new",
        element: <CampaignWizard mode="create" />,
      },
      {
        path: "campaigns/:campaignId",
        element: <CampaignDetail />,
      },
      {
        path: "campaigns/:campaignId/edit",
        element: <CampaignWizard mode="edit" />,
      },
      {
        path: "journey",
        element: (
          <Placeholder title="자동화 플로우" domain="journey" sprint="Sprint 8" />
        ),
      },
      {
        path: "templates",
        element: <Templates />,
      },
      {
        // 분석 도메인 허브 — 대시보드/펀널/매트릭스/감사 로그 진입점.
        path: "analytics",
        element: <AnalyticsHome />,
      },
      {
        // S14P31B101-90 — 캠페인 단계 펀널. 컴포넌트 내부에서 OWNER 가드.
        path: "analytics/funnel",
        element: <CampaignFunnel />,
      },
      {
        // S14P31B101-92 — 세그먼트 × 채널 성과 매트릭스. 컴포넌트 내부에서 OWNER 가드.
        path: "analytics/segment-channel",
        element: <SegmentChannelMatrix />,
      },
      {
        path: "settings",
        element: <SettingsLayout />,
      },
    ],
  },
  {
    path: "*",
    element: <NotFound />,
  },
]);
