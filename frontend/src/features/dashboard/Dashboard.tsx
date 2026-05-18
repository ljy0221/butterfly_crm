import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Card,
  ChannelBadge,
  Icon,
  Menu,
  PageHeader,
  StatCard,
  Tag,
} from "@/components/common";
import { useProjectStore } from "@/stores/project";
import {
  useChannelMetricsQuery,
  useDashboardQuery,
  useRecentCampaignsQuery,
} from "@/hooks/queries/analytics";
import { useCreditBalanceQuery } from "@/hooks/queries/payment";
import type {
  CampaignMetricItem,
  ChannelMetricItem,
  DailyPoint,
  DashboardSummary,
} from "@/api/analytics";
import type { CreditBalanceResponse } from "@/api/payment";
import {
  dailyTrendToPath,
  formatPeriod,
  formatPct,
  getDefaultPeriod,
  pct,
} from "@/utils/analytics";
import { fmtNum } from "@/utils/fmt.js";

/**
 * Sprint 3 — 대시보드 메인 페이지.
 *
 * 기존 mock Dashboard.jsx의 레이아웃·SVG 골격을 보존하고 실 API에 연결한다.
 *  - GET /api/projects/{projectId}/analytics/dashboard       (summary + dailyTrend)
 *  - GET /api/projects/{projectId}/analytics/channels        (채널별 비중)
 *  - GET /api/projects/{projectId}/analytics/campaigns?p&s&sort  (최근 5건)
 *
 * KPI 4종 (기간 캠페인 / 총 발송 / 평균 전달률 / 평균 클릭률):
 *  - avgDeliveryRate: delivered / sent
 *  - avgClickRate: ADR-0007 supersede 기준 click / open. 단 백엔드 v1 record(double)가
 *    아직 nullable로 갱신되지 않아 분모 0이면 0이 들어올 수도 있다 — formatRate가 null과
 *    NaN을 모두 "—"로 처리하므로 사용자에게 NaN%/0%가 잘못 노출되는 일은 없다.
 *  - delta·이전 기간 비교는 1차 출시에서 생략.
 *
 * 비율 표시는 모두 formatRate(value: number | null) — 분모 0 ⇒ "—" + 툴팁 (KPI 정의서 §3).
 */

type PeriodKey = "7" | "30" | "90";

const PERIOD_LABEL: Record<PeriodKey, string> = {
  "7": "최근 7일",
  "30": "최근 30일",
  "90": "최근 90일",
};

const CHART_W = 700;
const CHART_H = 220;

/** 차트 라인 색상 — gBlue gradient 보존 + delivered/clicked 톤 분리 */
const LINE_COLORS = {
  sent: "#3b5bdb",
  delivered: "#16a34a",
  clicked: "#f59e0b",
} as const;

export default function Dashboard() {
  const navigate = useNavigate();
  const project = useProjectStore((s) => s.current);
  const projectId = project?.projectId ?? null;
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const isOwner = role === "OWNER";

  const [periodKey, setPeriodKey] = useState<PeriodKey>("30");
  const period = useMemo(
    () => getDefaultPeriod(parseInt(periodKey, 10)),
    [periodKey],
  );

  const dashboardQ = useDashboardQuery({
    projectId,
    from: period.from,
    to: period.to,
  });
  const channelsQ = useChannelMetricsQuery({
    projectId,
    from: period.from,
    to: period.to,
  });
  const recentQ = useRecentCampaignsQuery({
    projectId,
    from: period.from,
    to: period.to,
    size: 5,
  });
  const creditBalanceQ = useCreditBalanceQuery(projectId, isOwner);

  const goCampaignDetail = (campaignId: number) => {
    navigate(`/p/${projectId}/campaigns/${campaignId}`);
  };

  const goNewCampaign = () => navigate(`/p/${projectId}/campaigns/new`);
  const goCampaignsList = () => navigate(`/p/${projectId}/campaigns`);

  return (
    <div>
      <PageHeader
        title="대시보드"
        subtitle={`${PERIOD_LABEL[periodKey]} 기준 · ${formatPeriod(period.from, period.to)}`}
        actions={
          <>
            <Menu
              align="right"
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
            {canEdit && (
              <Button
                variant="primaryStrong"
                size="sm"
                icon={Icon.plus(12)}
                onClick={goNewCampaign}
              >
                새 캠페인
              </Button>
            )}
          </>
        }
      />

      {/* KPI 6 cards (ADR-0007: OPEN/CONVERT 추가) */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(6, 1fr)",
          gap: 14,
          marginBottom: 20,
        }}
      >
        <KpiBlock
          summary={dashboardQ.data?.summary}
          isLoading={dashboardQ.isLoading}
          isError={dashboardQ.isError}
        />
      </div>

      {/* Trend + Channel mix */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "2fr 1fr",
          gap: 14,
          marginBottom: 20,
        }}
      >
        <Card
          title="발송 추이"
          subtitle={`일별 · ${PERIOD_LABEL[periodKey]}`}
          action={
            <div style={{ display: "flex", gap: 6 }}>
              <Tag tone="info" dot>
                전송
              </Tag>
              <Tag tone="success" dot>
                전달
              </Tag>
              <Tag tone="warning" dot>
                클릭
              </Tag>
            </div>
          }
        >
          <TrendChart
            trend={dashboardQ.data?.dailyTrend ?? []}
            isLoading={dashboardQ.isLoading}
            isError={dashboardQ.isError}
            onRetry={() => dashboardQ.refetch()}
          />
        </Card>
        <Card title="채널별 발송 비중" subtitle={PERIOD_LABEL[periodKey]}>
          <ChannelMix
            channels={channelsQ.data?.channels ?? []}
            isLoading={channelsQ.isLoading}
            isError={channelsQ.isError}
            onRetry={() => channelsQ.refetch()}
          />
        </Card>
      </div>

      {/* Recent campaigns + Quick actions */}
      <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>

      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr", gap: 14 }}>
        <Card
          title="최근 캠페인"
          subtitle="최근 발송 5건"
          action={
            <span
              role="button"
              tabIndex={0}
              onClick={goCampaignsList}
              onKeyDown={(e) => {
                if (e.key === "Enter") goCampaignsList();
              }}
              style={{
                fontSize: 12,
                color: "#2563eb",
                fontWeight: 600,
                cursor: "pointer",
              }}
            >
              전체 보기 →
            </span>
          }
        >
          <RecentCampaignTable
            items={recentQ.data?.content ?? []}
            isLoading={recentQ.isLoading}
            isError={recentQ.isError}
            canEdit={canEdit}
            onRowClick={goCampaignDetail}
            onCreateCampaign={goNewCampaign}
            onRetry={() => recentQ.refetch()}
          />
        </Card>
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {isOwner && (
            <CreditBalanceCard
              projectId={projectId}
              balance={creditBalanceQ.data}
              isLoading={creditBalanceQ.isLoading}
              isError={creditBalanceQ.isError}
              onRetry={() => creditBalanceQ.refetch()}
            />
          )}
          <Card title="빠른 작업">
            <QuickActions canEdit={canEdit} projectId={projectId} />
          </Card>
        </div>
      </div>
    </div>
  );
}

// ============================================================
// KPI 블록
// ============================================================

interface KpiBlockProps {
  summary?: DashboardSummary;
  isLoading: boolean;
  isError: boolean;
}

function KpiBlock({ summary, isLoading, isError }: KpiBlockProps) {
  if (isLoading) {
    return (
      <>
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <SkeletonCard key={i} height={108} />
        ))}
      </>
    );
  }
  if (isError || !summary) {
    return (
      <>
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <ErrorCard key={i} height={108} />
        ))}
      </>
    );
  }

  return (
    <>
      <StatCard
        label="기간 내 캠페인"
        value={fmtNum(summary.campaignsSentInPeriod) as string}
      />
      <StatCard label="총 발송" value={fmtNum(summary.totalSent) as string} />
      <StatCard label="평균 전달률" value={formatPct(summary.avgDeliveryRate)} />
      {/*
        ADR-0007 (2026-04-28 supersede ADR-0003) 적용 후 KPI 4종이 6종으로 확장.
        - avgOpenRate    = open / delivered
        - avgClickRate   = click / open  (이전 click/delivered 에서 분모 변경)
        - avgConvertRate = convert / sent
        분모가 0이면 백엔드가 null을 보내고 formatPct가 "—"로 표기 (KPI 정의서 §3).
        백엔드는 0~100 범위(DECIMAL 5,2)로 내려주므로 formatPct로 직접 포맷.
      */}
      <StatCard label="평균 오픈률" value={formatPct(summary.avgOpenRate)} />
      <StatCard label="평균 클릭률" value={formatPct(summary.avgClickRate)} />
      <StatCard label="평균 전환률" value={formatPct(summary.avgConvertRate)} />
    </>
  );
}

// ============================================================
// 발송 추이 차트
// ============================================================

interface TrendChartProps {
  trend: DailyPoint[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}

function TrendChart({ trend, isLoading, isError, onRetry }: TrendChartProps) {
  if (isLoading) {
    return <ChartSkeleton />;
  }
  if (isError) {
    return <ErrorBox message="발송 추이를 불러오지 못했습니다" onRetry={onRetry} />;
  }
  if (trend.length === 0) {
    return (
      <EmptyBox message="이 기간에는 발송 데이터가 없습니다" minHeight={CHART_H} />
    );
  }

  // 동일 maxY로 3개 라인 정규화
  const maxY = trend.reduce(
    (acc, p) => Math.max(acc, p.sent, p.delivered, p.clicked),
    0,
  );
  const yMax = maxY > 0 ? maxY : 1;
  const sentPath = dailyTrendToPath(trend, "sent", CHART_W, CHART_H, yMax);
  const deliveredPath = dailyTrendToPath(
    trend,
    "delivered",
    CHART_W,
    CHART_H,
    yMax,
  );
  const clickedPath = dailyTrendToPath(
    trend,
    "clicked",
    CHART_W,
    CHART_H,
    yMax,
  );

  // sent 라인 아래쪽 area fill용 path
  const stepX = trend.length === 1 ? 0 : CHART_W / (trend.length - 1);
  const sentArea = `${sentPath} L${(stepX * (trend.length - 1)).toFixed(2)},${CHART_H} L0,${CHART_H} Z`;

  // X축 라벨 — 5개로 균등 샘플링
  const labelCount = Math.min(5, trend.length);
  const labelIndices = Array.from({ length: labelCount }, (_, i) =>
    Math.round((i * (trend.length - 1)) / Math.max(1, labelCount - 1)),
  );

  const ariaLabel = `발송 추이 차트. ${trend.length}일치 데이터. 전송, 전달, 클릭 3개 라인.`;

  return (
    <svg
      role="img"
      aria-label={ariaLabel}
      viewBox={`0 0 ${CHART_W} ${CHART_H}`}
      style={{ width: "100%", height: CHART_H }}
    >
      <defs>
        <linearGradient id="gBlue" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={LINE_COLORS.sent} stopOpacity="0.18" />
          <stop offset="100%" stopColor={LINE_COLORS.sent} stopOpacity="0" />
        </linearGradient>
      </defs>
      {/* horizontal gridlines */}
      {[0, 0.25, 0.5, 0.75, 1].map((r, i) => (
        <line
          key={i}
          x1="0"
          y1={CHART_H * r}
          x2={CHART_W}
          y2={CHART_H * r}
          stroke="#f1f5f9"
        />
      ))}
      {/* X-axis labels */}
      {labelIndices.map((idx) => {
        const x = idx * stepX;
        const date = trend[idx]?.date ?? "";
        const short = date.length === 10 ? date.slice(5).replace("-", "/") : "";
        return (
          <text
            key={idx}
            x={x}
            y={CHART_H - 5}
            fontSize="9"
            fill="#94a3b8"
            textAnchor={
              idx === 0 ? "start" : idx === trend.length - 1 ? "end" : "middle"
            }
          >
            {short}
          </text>
        );
      })}
      {/* sent area fill */}
      <path d={sentArea} fill="url(#gBlue)" />
      {/* sent line */}
      <path
        d={sentPath}
        fill="none"
        stroke={LINE_COLORS.sent}
        strokeWidth="2"
      />
      {/* delivered line — dashed */}
      <path
        d={deliveredPath}
        fill="none"
        stroke={LINE_COLORS.delivered}
        strokeWidth="1.5"
        strokeDasharray="4 3"
      />
      {/* clicked line */}
      <path
        d={clickedPath}
        fill="none"
        stroke={LINE_COLORS.clicked}
        strokeWidth="1.5"
      />
    </svg>
  );
}

// ============================================================
// 채널별 발송 비중
// ============================================================

interface ChannelMixProps {
  channels: ChannelMetricItem[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}

const CHANNEL_DISPLAY: Record<string, { label: string; color: string }> = {
  PUSH: { label: "FCM 푸시", color: "#3b5bdb" },
  FCM: { label: "FCM 푸시", color: "#3b5bdb" },
  EMAIL: { label: "이메일", color: "#2563eb" },
  SMS: { label: "SMS / LMS", color: "#64748b" },
  LMS: { label: "SMS / LMS", color: "#64748b" },
  WEBHOOK: { label: "웹훅", color: "#94a3b8" },
};

function ChannelMix({ channels, isLoading, isError, onRetry }: ChannelMixProps) {
  if (isLoading) {
    return (
      <div>
        {[0, 1, 2, 3].map((i) => (
          <div key={i} style={{ marginBottom: 14 }}>
            <SkeletonBar height={12} width="60%" />
            <div style={{ height: 6, marginTop: 8 }}>
              <SkeletonBar height={6} width="100%" />
            </div>
          </div>
        ))}
      </div>
    );
  }
  if (isError) {
    return <ErrorBox message="채널 비중을 불러오지 못했습니다" onRetry={onRetry} />;
  }
  if (channels.length === 0) {
    return <EmptyBox message="채널 데이터가 없습니다" />;
  }

  const totalSent = channels.reduce((sum, c) => sum + c.sentCount, 0);

  return (
    <div>
      {channels.map((c) => {
        const display = CHANNEL_DISPLAY[c.channel] ?? {
          label: c.channel,
          color: "#94a3b8",
        };
        const ratio = pct(c.sentCount, totalSent);
        return (
          <div key={c.channel} style={{ marginBottom: 14 }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                fontSize: 12,
                marginBottom: 6,
              }}
            >
              <span style={{ color: "#1f2937", fontWeight: 500 }}>
                {display.label}
              </span>
              <span
                style={{
                  color: "#64748b",
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                {ratio.toFixed(1)}%
              </span>
            </div>
            <div
              style={{
                height: 6,
                borderRadius: 3,
                background: "#f1f5f9",
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  height: "100%",
                  width: `${Math.max(0, Math.min(100, ratio))}%`,
                  background: display.color,
                  borderRadius: 3,
                }}
              />
            </div>
            <div
              style={{
                fontSize: 10,
                color: "#94a3b8",
                marginTop: 3,
                fontVariantNumeric: "tabular-nums",
              }}
            >
              {fmtNum(c.sentCount)} 건
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ============================================================
// 최근 캠페인 5건 테이블
// ============================================================

interface RecentCampaignTableProps {
  items: CampaignMetricItem[];
  isLoading: boolean;
  isError: boolean;
  canEdit: boolean;
  onRowClick: (campaignId: number) => void;
  onCreateCampaign: () => void;
  onRetry: () => void;
}

function RecentCampaignTable({
  items,
  isLoading,
  isError,
  canEdit,
  onRowClick,
  onCreateCampaign,
  onRetry,
}: RecentCampaignTableProps) {
  // ADR-0007 적용: 발송수 / 전달률 / 오픈률 / 클릭률 / 전환률 5개 metric 컬럼.
  const cols = "2.4fr 0.9fr 1fr 0.8fr 0.8fr 0.8fr 0.8fr";

  if (isLoading) {
    return (
      <div role="table" style={{ margin: "-4px -4px" }}>
        <div
          role="row"
          style={{
            display: "grid",
            gridTemplateColumns: cols,
            padding: "10px 4px",
            fontSize: 11,
            color: "#64748b",
            fontWeight: 600,
            borderBottom: "1px solid #f1f5f9",
          }}
        >
          <div role="cell">캠페인</div>
          <div role="cell">채널</div>
          <div role="cell">발송수</div>
          <div role="cell">전달률</div>
          <div role="cell">오픈률</div>
          <div role="cell">클릭률</div>
          <div role="cell">전환률</div>
        </div>
        {[0, 1, 2, 3, 4].map((i) => (
          <div
            key={i}
            style={{
              display: "grid",
              gridTemplateColumns: cols,
              padding: "12px 4px",
              borderBottom: i < 4 ? "1px solid #f8fafc" : "none",
              alignItems: "center",
            }}
          >
            <SkeletonBar height={14} width="80%" />
            <SkeletonBar height={14} width="60%" />
            <SkeletonBar height={14} width="50%" />
            <SkeletonBar height={14} width="40%" />
            <SkeletonBar height={14} width="40%" />
            <SkeletonBar height={14} width="40%" />
            <SkeletonBar height={14} width="40%" />
          </div>
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorBox message="최근 캠페인을 불러오지 못했습니다" onRetry={onRetry} />
    );
  }

  if (items.length === 0) {
    return (
      <div
        style={{
          padding: "32px 8px",
          textAlign: "center",
          color: "#94a3b8",
          fontSize: 13,
        }}
      >
        <div>최근 발송한 캠페인이 없습니다</div>
        {canEdit && (
          <div style={{ marginTop: 12 }}>
            <Button
              variant="primary"
              size="sm"
              icon={Icon.plus(12)}
              onClick={onCreateCampaign}
            >
              캠페인 만들기
            </Button>
          </div>
        )}
      </div>
    );
  }

  return (
    <div role="table" style={{ margin: "-4px -4px" }}>
      <div
        role="row"
        style={{
          display: "grid",
          gridTemplateColumns: cols,
          padding: "10px 4px",
          fontSize: 11,
          color: "#64748b",
          fontWeight: 600,
          borderBottom: "1px solid #f1f5f9",
        }}
      >
        <div role="cell">캠페인</div>
        <div role="cell">채널</div>
        <div role="cell">발송수</div>
        <div role="cell">전달률</div>
        <div role="cell">오픈률</div>
        <div role="cell">클릭률</div>
        <div role="cell">전환률</div>
      </div>
      {items.map((c, i) => (
        <div
          key={c.campaignId}
          role="row"
          tabIndex={0}
          onClick={() => onRowClick(c.campaignId)}
          onKeyDown={(e) => {
            if (e.key === "Enter") onRowClick(c.campaignId);
          }}
          style={{
            display: "grid",
            gridTemplateColumns: cols,
            padding: "12px 4px",
            alignItems: "center",
            fontSize: 12,
            cursor: "pointer",
            borderBottom: i < items.length - 1 ? "1px solid #f8fafc" : "none",
            outline: "none",
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = "#f8fafc";
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = "transparent";
          }}
        >
          <div
            role="cell"
            style={{
              color: "#1f2937",
              fontWeight: 500,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {c.campaignName}
          </div>
          <div role="cell">
            <ChannelBadge channel={c.channel} />
          </div>
          <div
            role="cell"
            style={{
              color: "#1f2937",
              fontVariantNumeric: "tabular-nums",
            }}
          >
            {fmtNum(c.sentCount)}
          </div>
          <RateCell value={c.deliveryRate} color="#16a34a" />
          <RateCell value={c.openRate} color="#0ea5e9" />
          <RateCell value={c.clickRate} color="#f59e0b" />
          <RateCell value={c.convertRate} color="#7c3aed" />
        </div>
      ))}
    </div>
  );
}

// ============================================================
// 비율 셀 (recent campaigns 테이블 — ADR-0007 4종 비율 일관 표시)
// ============================================================

function RateCell({
  value,
  color,
}: {
  value: number | null;
  color: string;
}) {
  const text = formatPct(value);
  const isMissing = value === null;
  return (
    <div
      role="cell"
      style={{
        color: isMissing ? "#94a3b8" : color,
        fontVariantNumeric: "tabular-nums",
        fontWeight: isMissing ? 400 : 600,
      }}
      title={isMissing ? "분모가 0이라 비율을 계산할 수 없습니다" : undefined}
    >
      {text}
    </div>
  );
}

// ============================================================
// 크레딧 요약
// ============================================================

interface CreditBalanceCardProps {
  projectId: number | null;
  balance?: CreditBalanceResponse;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}

function CreditBalanceCard({
  projectId,
  balance,
  isLoading,
  isError,
  onRetry,
}: CreditBalanceCardProps) {
  const navigate = useNavigate();

  return (
    <Card
      title="크레딧"
      subtitle="캠페인 발송 예약 기준"
      action={
        <Button
          variant="secondary"
          size="sm"
          icon={Icon.wallet(12)}
          onClick={() => {
            if (projectId != null) navigate(`/p/${projectId}/credits/charge`);
          }}
          disabled={projectId == null}
        >
          충전
        </Button>
      }
    >
      {isLoading ? (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <SkeletonBar height={24} width="62%" />
          <SkeletonBar height={12} width="100%" />
          <SkeletonBar height={12} width="82%" />
        </div>
      ) : isError ? (
        <ErrorBox message="크레딧 잔액을 불러오지 못했습니다." onRetry={onRetry} />
      ) : (
        <div>
          <div style={{ fontSize: 12, color: "#64748b", marginBottom: 4 }}>
            사용 가능
          </div>
          <div
            style={{
              fontSize: 26,
              fontWeight: 800,
              color: "#1f2937",
              fontVariantNumeric: "tabular-nums",
            }}
          >
            {balance ? balance.availableBalance.toLocaleString() : "—"} 크레딧
          </div>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
              gap: 8,
              marginTop: 14,
            }}
          >
            <CreditMiniStat label="총 보유" value={balance?.balance} />
            <CreditMiniStat label="예약 중" value={balance?.reservedBalance} />
          </div>
        </div>
      )}
    </Card>
  );
}

function CreditMiniStat({ label, value }: { label: string; value?: number }) {
  return (
    <div
      style={{
        border: "1px solid #e2e8f0",
        borderRadius: 8,
        padding: "9px 10px",
        background: "#f8fafc",
      }}
    >
      <div style={{ fontSize: 11, color: "#64748b", marginBottom: 4 }}>{label}</div>
      <div
        style={{
          fontSize: 12,
          color: "#1f2937",
          fontWeight: 700,
          fontVariantNumeric: "tabular-nums",
        }}
      >
        {value != null ? value.toLocaleString() : "—"}
      </div>
    </div>
  );
}

// ============================================================
// 빠른 작업
// ============================================================

interface QuickActionsProps {
  canEdit: boolean;
  projectId: number | null;
}

function QuickActions({ canEdit, projectId }: QuickActionsProps) {
  const navigate = useNavigate();
  if (projectId == null) return null;

  const items: Array<{
    label: string;
    desc: string;
    icon: React.ReactNode;
    path: string;
    requiresEdit: boolean;
  }> = [
    {
      label: "세그먼트 만들기",
      desc: "조건 조합으로 타겟 정의",
      icon: Icon.segments(16),
      path: `/p/${projectId}/segments/new`,
      requiresEdit: true,
    },
    {
      label: "캠페인 만들기",
      desc: "템플릿과 세그먼트 선택",
      icon: Icon.campaigns(16),
      path: `/p/${projectId}/campaigns/new`,
      requiresEdit: true,
    },
    {
      label: "이벤트 스키마 설정",
      desc: "속성 추가·타입 변경",
      icon: Icon.events(16),
      path: `/p/${projectId}/events`,
      requiresEdit: false,
    },
    {
      label: "성과 리포트",
      desc: "캠페인별 전환·오픈 분석",
      icon: Icon.analytics(16),
      path: `/p/${projectId}/analytics`,
      requiresEdit: false,
    },
  ];

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      {items.map((q) => {
        const disabled = q.requiresEdit && !canEdit;
        return (
          <div
            key={q.label}
            role="button"
            tabIndex={disabled ? -1 : 0}
            aria-disabled={disabled || undefined}
            title={disabled ? "EDITOR 권한 필요" : undefined}
            onClick={() => {
              if (disabled) return;
              navigate(q.path);
            }}
            onKeyDown={(e) => {
              if (disabled) return;
              if (e.key === "Enter") navigate(q.path);
            }}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 12,
              padding: "10px 12px",
              border: "1px solid #e2e8f0",
              borderRadius: 8,
              cursor: disabled ? "not-allowed" : "pointer",
              transition: "background 120ms",
              background: "#fff",
              opacity: disabled ? 0.5 : 1,
            }}
            onMouseEnter={(e) => {
              if (disabled) return;
              e.currentTarget.style.background = "#f8fafc";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "#fff";
            }}
          >
            <div
              style={{
                width: 32,
                height: 32,
                borderRadius: 8,
                background: "rgba(59,91,219,.08)",
                color: "#3b5bdb",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {q.icon}
            </div>
            <div style={{ flex: 1 }}>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  color: "#1f2937",
                }}
              >
                {q.label}
              </div>
              <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 1 }}>
                {q.desc}
              </div>
            </div>
            <span style={{ color: "#cbd5e1" }}>{Icon.chevron(12)}</span>
          </div>
        );
      })}
    </div>
  );
}

// ============================================================
// 공용 상태 표현
// ============================================================

function SkeletonCard({ height }: { height: number }) {
  return (
    <div
      aria-hidden
      style={{
        height,
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 12,
        padding: "18px 20px",
        display: "flex",
        flexDirection: "column",
        gap: 10,
      }}
    >
      <SkeletonBar height={11} width="40%" />
      <SkeletonBar height={26} width="60%" />
      <SkeletonBar height={10} width="50%" />
    </div>
  );
}

function SkeletonBar({
  height,
  width,
}: {
  height: number;
  width: number | string;
}) {
  return (
    <div
      aria-hidden
      style={{
        height,
        width,
        background:
          "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
        backgroundSize: "200% 100%",
        borderRadius: 4,
        animation: "shimmer 1.4s ease-in-out infinite",
      }}
    />
  );
}

function ChartSkeleton() {
  return (
    <div
      aria-hidden
      style={{
        height: CHART_H,
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
        padding: "8px 0",
      }}
    >
      {[0, 1, 2, 3, 4].map((i) => (
        <div
          key={i}
          style={{ height: 1, background: "#f1f5f9", width: "100%" }}
        />
      ))}
    </div>
  );
}

function EmptyBox({
  message,
  minHeight = 120,
}: {
  message: string;
  minHeight?: number;
}) {
  return (
    <div
      style={{
        minHeight,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "#94a3b8",
        fontSize: 13,
      }}
    >
      {message}
    </div>
  );
}

function ErrorCard({ height }: { height: number }) {
  return (
    <div
      style={{
        height,
        background: "#fff",
        border: "1px solid #fecaca",
        borderRadius: 12,
        padding: "18px 20px",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        color: "#b91c1c",
        fontSize: 12,
      }}
    >
      데이터를 불러오지 못했습니다
    </div>
  );
}

function ErrorBox({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div
      style={{
        minHeight: 120,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 10,
        color: "#b91c1c",
        fontSize: 13,
      }}
    >
      <span>{message}</span>
      <Button variant="secondary" size="sm" onClick={onRetry}>
        재시도
      </Button>
    </div>
  );
}
