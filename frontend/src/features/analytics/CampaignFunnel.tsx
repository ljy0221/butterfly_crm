import { useMemo, useState } from "react";
import { Button, Card, Icon, Menu, PageHeader, Tag } from "@/components/common";
import { useProjectStore } from "@/stores/project";
import { useCampaignFunnelQuery } from "@/hooks/queries/report";
import type {
  CampaignFunnelStageRow,
  Channel,
  FunnelStage,
} from "@/api/report";
import { formatPeriod, formatRate, getDefaultPeriod } from "@/utils/analytics";
import { fmtNum } from "@/utils/fmt.js";

/**
 * S14P31B101-90 — 캠페인 단계 펀널 페이지.
 *
 * 백엔드: GET /api/v1/projects/{projectId}/reports/campaign-funnel
 *  - 권한: OWNER 전용
 *  - 응답: 5단계 (SENT → DELIVERED → OPEN → CLICK → CONVERT) + biggestDropOffStage
 *
 * 비율 3종(conversionFromStart/conversionFromPrev/dropOffFromPrev)은 분모 0이면 null.
 */

type PeriodKey = "7" | "30" | "90";

const PERIOD_LABEL: Record<PeriodKey, string> = {
  "7": "최근 7일",
  "30": "최근 30일",
  "90": "최근 90일",
};

const ALL_CHANNELS: Channel[] = ["PUSH", "EMAIL", "SMS", "WEBHOOK", "KAKAO"];

const CHANNEL_LABEL: Record<Channel, string> = {
  PUSH: "앱 푸시",
  EMAIL: "이메일",
  SMS: "SMS",
  WEBHOOK: "웹훅",
  KAKAO: "카카오 알림톡",
};

const STAGE_LABEL: Record<FunnelStage, string> = {
  SENT: "발송",
  DELIVERED: "전달",
  OPEN: "오픈",
  CLICK: "클릭",
  CONVERT: "전환",
};

const STAGE_COLOR: Record<FunnelStage, string> = {
  SENT: "#3b5bdb",
  DELIVERED: "#16a34a",
  OPEN: "#0ea5e9",
  CLICK: "#f59e0b",
  CONVERT: "#7c3aed",
};

export default function CampaignFunnel({ embedded = false }: { embedded?: boolean }) {
  const project = useProjectStore((s) => s.current);
  const projectId = project?.projectId ?? null;
  const role = project?.role ?? "VIEWER";
  const isOwner = role === "OWNER";

  const [periodKey, setPeriodKey] = useState<PeriodKey>("30");
  const [channel, setChannel] = useState<Channel | undefined>(undefined);

  const period = useMemo(
    () => getDefaultPeriod(parseInt(periodKey, 10)),
    [periodKey],
  );

  const funnelQ = useCampaignFunnelQuery({
    projectId,
    from: period.from,
    to: period.to,
    channel,
    enabled: isOwner,
  });

  if (!isOwner) {
    return <DenyOwnerOnly currentRole={role} embedded={embedded} />;
  }

  const filterBar = (
    <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
      <Menu
        align="left"
        trigger={
          <Button
            variant="secondary"
            size="sm"
            icon={Icon.calendar(12)}
            iconRight={<span style={{ fontSize: 9 }}>▾</span>}
          >
            {PERIOD_LABEL[periodKey]}
          </Button>
        }
        items={(Object.keys(PERIOD_LABEL) as PeriodKey[]).map((k) => ({
          label: PERIOD_LABEL[k],
          onClick: () => setPeriodKey(k),
        }))}
      />
      <Menu
        align="left"
        trigger={
          <Button
            variant="secondary"
            size="sm"
            iconRight={<span style={{ fontSize: 9 }}>▾</span>}
          >
            채널: {channel ? CHANNEL_LABEL[channel] : "전체"}
          </Button>
        }
        items={[
          { label: "전체", onClick: () => setChannel(undefined) },
          ...ALL_CHANNELS.map((c) => ({
            label: CHANNEL_LABEL[c],
            onClick: () => setChannel(c),
          })),
        ]}
      />
      <span style={{ alignSelf: "center", fontSize: 12, color: "#94a3b8" }}>
        {formatPeriod(period.from, period.to)}
      </span>
    </div>
  );

  return (
    <div>
      {!embedded && (
        <PageHeader
          title="캠페인 단계 펀널"
          subtitle={`${PERIOD_LABEL[periodKey]} 기준 · ${formatPeriod(period.from, period.to)}`}
        />
      )}

      {filterBar}

      {/* 가장 큰 이탈 단계 강조 */}
      {funnelQ.data?.biggestDropOffStage && (
        <Card>
          <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "8px 0" }}>
            <span style={{ fontSize: 24 }}>⚠️</span>
            <div>
              <div style={{ fontSize: 12, color: "#64748b", marginBottom: 2 }}>
                가장 큰 이탈 단계
              </div>
              <div style={{ fontSize: 16, fontWeight: 600, color: "#1f2937" }}>
                {STAGE_LABEL[funnelQ.data.biggestDropOffStage]} 직전
              </div>
            </div>
          </div>
        </Card>
      )}

      <div style={{ height: 16 }} />

      <Card title="펀널" subtitle={channel ? CHANNEL_LABEL[channel] : "모든 채널"}>
        <FunnelView
          stages={funnelQ.data?.stages ?? []}
          isLoading={funnelQ.isLoading}
          isError={funnelQ.isError}
          onRetry={() => funnelQ.refetch()}
        />
      </Card>
    </div>
  );
}

// ============================================================
// Funnel 본문 (가로 막대형)
// ============================================================

interface FunnelViewProps {
  stages: CampaignFunnelStageRow[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}

function FunnelView({ stages, isLoading, isError, onRetry }: FunnelViewProps) {
  if (isLoading) {
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 12, padding: "8px 4px" }}>
        {[0, 1, 2, 3, 4].map((i) => (
          <SkeletonBar key={i} height={48} width="100%" />
        ))}
      </div>
    );
  }

  if (isError) {
    return <ErrorBox message="펀널을 불러오지 못했습니다" onRetry={onRetry} />;
  }

  if (stages.length === 0) {
    return (
      <div
        style={{
          padding: "40px 8px",
          textAlign: "center",
          color: "#94a3b8",
          fontSize: 13,
        }}
      >
        선택한 기간/채널에 해당하는 발송 이력이 없습니다.
      </div>
    );
  }

  // 첫 단계(SENT)를 100% 폭으로 두고 나머지 폭 = count / startCount.
  const startCount = stages[0]?.count ?? 0;
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10, padding: "8px 4px" }}>
      {stages.map((row) => {
        const widthPct =
          startCount > 0 ? Math.max(2, (row.count / startCount) * 100) : 0;
        return (
          <div key={row.stage}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "baseline",
                fontSize: 12,
                color: "#475569",
                marginBottom: 4,
              }}
            >
              <span style={{ fontWeight: 600, color: "#1f2937" }}>
                {STAGE_LABEL[row.stage]}
              </span>
              <span style={{ fontVariantNumeric: "tabular-nums" }}>
                {fmtNum(row.count)} · 시작 대비 {formatRate(row.conversionFromStart != null ? row.conversionFromStart / 100 : null)} ·
                직전 통과 {formatRate(row.conversionFromPrev != null ? row.conversionFromPrev / 100 : null)} · 이탈 {formatRate(row.dropOffFromPrev != null ? row.dropOffFromPrev / 100 : null)}
              </span>
            </div>
            <div
              style={{
                position: "relative",
                height: 28,
                background: "#f1f5f9",
                borderRadius: 6,
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  position: "absolute",
                  inset: 0,
                  width: `${widthPct}%`,
                  background: STAGE_COLOR[row.stage],
                  opacity: 0.85,
                  transition: "width 0.3s ease",
                }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ============================================================
// 보조 컴포넌트
// ============================================================

function SkeletonBar({
  height,
  width,
}: {
  height: number;
  width: number | string;
}) {
  return (
    <>
      <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>
      <div
        style={{
          height,
          width,
          borderRadius: 6,
          background:
            "linear-gradient(90deg,#f1f5f9 25%,#e2e8f0 37%,#f1f5f9 63%) 0/200% 100%",
          animation: "shimmer 1.4s linear infinite",
        }}
      />
    </>
  );
}

interface ErrorBoxProps {
  message: string;
  onRetry: () => void;
}

function ErrorBox({ message, onRetry }: ErrorBoxProps) {
  return (
    <div
      style={{
        padding: 24,
        textAlign: "center",
        background: "#fff7f7",
        border: "1px solid #fecaca",
        borderRadius: 8,
      }}
    >
      <div style={{ color: "#b91c1c", fontSize: 13, marginBottom: 12 }}>
        {message}
      </div>
      <Button variant="secondary" size="sm" onClick={onRetry}>
        다시 시도
      </Button>
    </div>
  );
}

function DenyOwnerOnly({ currentRole, embedded }: { currentRole: string; embedded?: boolean }) {
  return (
    <div>
      {!embedded && <PageHeader title="캠페인 단계 펀널" />}
      <Card>
        <div style={{ padding: 32, textAlign: "center", color: "#475569" }}>
          <div style={{ fontSize: 32, marginBottom: 8 }}>🔒</div>
          <h3 style={{ fontSize: 16, margin: "0 0 8px" }}>OWNER 권한이 필요합니다</h3>
          <p style={{ fontSize: 13, color: "#64748b", margin: 0 }}>
            캠페인 단계 펀널은 프로젝트 OWNER만 조회할 수 있습니다.
            (현재 권한: <Tag>{currentRole}</Tag>)
          </p>
        </div>
      </Card>
    </div>
  );
}
