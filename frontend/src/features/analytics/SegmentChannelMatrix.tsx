import { useMemo, useState } from "react";
import { Button, Card, ChannelBadge, Icon, Menu, PageHeader, StatCard, Tag } from "@/components/common";
import { useProjectStore } from "@/stores/project";
import { useSegmentChannelMatrixQuery } from "@/hooks/queries/report";
import type {
  Channel,
  SegmentChannelMatrixHighlights,
  SegmentChannelMatrixRow,
  SegmentChannelMatrixSortBy,
} from "@/api/report";
import { formatPeriod, formatRate, getDefaultPeriod } from "@/utils/analytics";
import { fmtNum } from "@/utils/fmt.js";

/**
 * S14P31B101-92 — 세그먼트 × 채널 성과 매트릭스 페이지.
 *
 * 백엔드: GET /api/v1/projects/{projectId}/reports/segment-channel-matrix
 *  - 권한: OWNER 전용 (@projectPermission.hasRole(#projectId, 'OWNER'))
 *  - 응답: rows + highlights (top/bottom by rate)
 *
 * 비율 지표는 분모가 0이면 null — formatRate 유틸로 일괄 "—" 처리.
 * KPI 정의는 ADR-0007: open_rate = open/delivered, click_rate = click/open,
 * convert_rate = convert/sent.
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

const SORT_LABEL: Record<SegmentChannelMatrixSortBy, string> = {
  SENT_COUNT: "발송수",
  DELIVERED_COUNT: "전달수",
  OPEN_RATE: "오픈률",
  CLICK_RATE: "클릭률",
  CONVERT_RATE: "전환률",
};

const SORT_KEYS: SegmentChannelMatrixSortBy[] = [
  "SENT_COUNT",
  "DELIVERED_COUNT",
  "OPEN_RATE",
  "CLICK_RATE",
  "CONVERT_RATE",
];

export default function SegmentChannelMatrix() {
  const project = useProjectStore((s) => s.current);
  const projectId = project?.projectId ?? null;
  const role = project?.role ?? "VIEWER";
  const isOwner = role === "OWNER";

  const [periodKey, setPeriodKey] = useState<PeriodKey>("30");
  const [channels, setChannels] = useState<Channel[]>([]);
  const [sortBy, setSortBy] = useState<SegmentChannelMatrixSortBy>("SENT_COUNT");
  const [ascending, setAscending] = useState<boolean>(false);

  const period = useMemo(
    () => getDefaultPeriod(parseInt(periodKey, 10)),
    [periodKey],
  );

  const matrixQ = useSegmentChannelMatrixQuery({
    projectId,
    from: period.from,
    to: period.to,
    channels: channels.length > 0 ? channels : undefined,
    sortBy,
    ascending,
    enabled: isOwner,
  });

  const toggleChannel = (c: Channel) => {
    setChannels((prev) =>
      prev.includes(c) ? prev.filter((x) => x !== c) : [...prev, c],
    );
  };

  if (!isOwner) {
    return <DenyOwnerOnly currentRole={role} />;
  }

  return (
    <div>
      <PageHeader
        title="세그먼트 × 채널 매트릭스"
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
            <Menu
              align="right"
              trigger={
                <Button
                  variant="secondary"
                  size="sm"
                  iconRight={<span style={{ fontSize: 9 }}>▾</span>}
                >
                  정렬: {SORT_LABEL[sortBy]} {ascending ? "오름차순" : "내림차순"}
                </Button>
              }
              items={[
                ...SORT_KEYS.map((k) => ({
                  label: SORT_LABEL[k],
                  onClick: () => setSortBy(k),
                })),
                {
                  label: ascending ? "내림차순으로" : "오름차순으로",
                  onClick: () => setAscending((v) => !v),
                },
              ]}
            />
            <Button
              variant="secondary"
              size="sm"
              icon={Icon.refresh ? Icon.refresh(12) : undefined}
              onClick={() => matrixQ.refetch()}
            >
              새로고침
            </Button>
          </>
        }
      />

      {/* 채널 필터 칩 */}
      <Card title="채널 필터" subtitle="모두 끄면 전체 채널 포함">
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {ALL_CHANNELS.map((c) => {
            const active = channels.includes(c);
            return (
              <button
                key={c}
                type="button"
                onClick={() => toggleChannel(c)}
                style={chipStyle(active)}
              >
                {CHANNEL_LABEL[c]}
              </button>
            );
          })}
          {channels.length > 0 && (
            <button
              type="button"
              onClick={() => setChannels([])}
              style={{
                ...chipStyle(false),
                color: "#64748b",
                borderStyle: "dashed",
              }}
            >
              초기화
            </button>
          )}
        </div>
      </Card>

      {/* 하이라이트 4 카드 */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: 14,
          margin: "20px 0",
        }}
      >
        <HighlightBlock
          highlights={matrixQ.data?.highlights}
          isLoading={matrixQ.isLoading}
          isError={matrixQ.isError}
        />
      </div>

      {/* 매트릭스 본문 */}
      <Card
        title="매트릭스"
        subtitle={
          matrixQ.data
            ? `${matrixQ.data.rows.length.toLocaleString()}개 조합`
            : undefined
        }
      >
        <MatrixTable
          rows={matrixQ.data?.rows ?? []}
          isLoading={matrixQ.isLoading}
          isError={matrixQ.isError}
          onRetry={() => matrixQ.refetch()}
        />
      </Card>
    </div>
  );
}

// ============================================================
// 하이라이트 카드
// ============================================================

interface HighlightBlockProps {
  highlights: SegmentChannelMatrixHighlights | undefined;
  isLoading: boolean;
  isError: boolean;
}

function HighlightBlock({ highlights, isLoading, isError }: HighlightBlockProps) {
  if (isLoading) {
    return (
      <>
        {[0, 1, 2, 3].map((i) => (
          <SkeletonCard key={i} height={108} />
        ))}
      </>
    );
  }
  if (isError || !highlights) {
    return (
      <>
        {[0, 1, 2, 3].map((i) => (
          <ErrorCard key={i} height={108} />
        ))}
      </>
    );
  }

  return (
    <>
      <HighlightCard
        label="오픈률 1위"
        row={highlights.topByOpenRate}
        rateOf={(r) => r.openRate}
      />
      <HighlightCard
        label="클릭률 1위"
        row={highlights.topByClickRate}
        rateOf={(r) => r.clickRate}
      />
      <HighlightCard
        label="전환률 1위"
        row={highlights.topByConvertRate}
        rateOf={(r) => r.convertRate}
      />
      <HighlightCard
        label="오픈률 최저"
        row={highlights.bottomByOpenRate}
        rateOf={(r) => r.openRate}
        tone="warning"
      />
    </>
  );
}

interface HighlightCardProps {
  label: string;
  row: SegmentChannelMatrixRow | null;
  rateOf: (r: SegmentChannelMatrixRow) => number | null;
  tone?: "default" | "warning";
}

function HighlightCard({ label, row, rateOf, tone = "default" }: HighlightCardProps) {
  if (!row) {
    return (
      <StatCard
        label={label}
        value="—"
        hint="데이터 부족"
      />
    );
  }
  const segLabel = row.segmentName ?? "세그먼트 미지정";
  const rateLabel = formatRate(rateOf(row));
  const subline = `${segLabel} · ${CHANNEL_LABEL[row.channel]}`;
  return (
    <StatCard
      label={label}
      value={rateLabel}
      hint={subline}
      tone={tone}
    />
  );
}

// ============================================================
// 매트릭스 테이블
// ============================================================

interface MatrixTableProps {
  rows: SegmentChannelMatrixRow[];
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}

const TABLE_COLS = "1.6fr 1fr 0.9fr 0.9fr 0.9fr 0.9fr 0.9fr 0.9fr";

function MatrixTable({ rows, isLoading, isError, onRetry }: MatrixTableProps) {
  if (isLoading) {
    return (
      <div role="table" style={{ margin: "-4px -4px" }}>
        <MatrixHeader />
        {[0, 1, 2, 3, 4].map((i) => (
          <div
            key={i}
            style={{
              display: "grid",
              gridTemplateColumns: TABLE_COLS,
              padding: "12px 4px",
              borderBottom: i < 4 ? "1px solid #f8fafc" : "none",
              alignItems: "center",
            }}
          >
            {Array.from({ length: 8 }).map((_, j) => (
              <SkeletonBar key={j} height={14} width={j === 0 ? "75%" : "50%"} />
            ))}
          </div>
        ))}
      </div>
    );
  }

  if (isError) {
    return (
      <ErrorBox message="매트릭스를 불러오지 못했습니다" onRetry={onRetry} />
    );
  }

  if (rows.length === 0) {
    return (
      <div
        style={{
          padding: "40px 8px",
          textAlign: "center",
          color: "#94a3b8",
          fontSize: 13,
        }}
      >
        선택한 기간/필터에 해당하는 발송 이력이 없습니다.
      </div>
    );
  }

  return (
    <div role="table" style={{ margin: "-4px -4px" }}>
      <MatrixHeader />
      {rows.map((r, i) => (
        <div
          key={`${r.segmentId ?? "none"}-${r.channel}-${i}`}
          role="row"
          style={{
            display: "grid",
            gridTemplateColumns: TABLE_COLS,
            padding: "12px 4px",
            borderBottom: i < rows.length - 1 ? "1px solid #f8fafc" : "none",
            alignItems: "center",
            fontSize: 13,
          }}
        >
          <div
            role="cell"
            style={{
              color: r.segmentName ? "#1f2937" : "#94a3b8",
              fontStyle: r.segmentName ? "normal" : "italic",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
            title={r.segmentName ?? "세그먼트 미지정"}
          >
            {r.segmentName ?? "세그먼트 미지정"}
          </div>
          <div role="cell">
            <ChannelBadge channel={CHANNEL_LABEL[r.channel]} />
          </div>
          <NumCell value={fmtNum(r.sentCount) as string} />
          <NumCell value={fmtNum(r.deliveredCount) as string} color="#16a34a" />
          <RateCell value={r.deliveryRate} color="#16a34a" />
          <RateCell value={r.openRate} color="#0ea5e9" />
          <RateCell value={r.clickRate} color="#f59e0b" />
          <RateCell value={r.convertRate} color="#7c3aed" />
        </div>
      ))}
    </div>
  );
}

function MatrixHeader() {
  return (
    <div
      role="row"
      style={{
        display: "grid",
        gridTemplateColumns: TABLE_COLS,
        padding: "10px 4px",
        fontSize: 11,
        color: "#64748b",
        fontWeight: 600,
        borderBottom: "1px solid #f1f5f9",
      }}
    >
      <div role="cell">세그먼트</div>
      <div role="cell">채널</div>
      <div role="cell">발송수</div>
      <div role="cell">전달수</div>
      <div role="cell">전달률</div>
      <div role="cell">오픈률</div>
      <div role="cell">클릭률</div>
      <div role="cell">전환률</div>
    </div>
  );
}

function NumCell({ value, color = "#1f2937" }: { value: string; color?: string }) {
  return (
    <div
      role="cell"
      style={{ color, fontVariantNumeric: "tabular-nums" }}
    >
      {value}
    </div>
  );
}

function RateCell({ value, color }: { value: number | null; color: string }) {
  const text = formatRate(value);
  const isMissing = text === "—";
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
// 보조 컴포넌트 (Dashboard.tsx 패턴 차용)
// ============================================================

function chipStyle(active: boolean): React.CSSProperties {
  return {
    height: 28,
    padding: "0 12px",
    borderRadius: 999,
    fontSize: 12,
    fontWeight: 600,
    fontFamily: "inherit",
    cursor: "pointer",
    background: active ? "#eef2ff" : "#fff",
    color: active ? "#3730a3" : "#475569",
    border: `1px solid ${active ? "#c7d2fe" : "#e2e8f0"}`,
  };
}

function SkeletonCard({ height }: { height: number }) {
  return (
    <div
      style={{
        height,
        borderRadius: 12,
        background:
          "linear-gradient(90deg,#f1f5f9 25%,#e2e8f0 37%,#f1f5f9 63%) 0/200% 100%",
        animation: "shimmer 1.4s linear infinite",
      }}
    />
  );
}

function ErrorCard({ height }: { height: number }) {
  return (
    <div
      style={{
        height,
        borderRadius: 12,
        background: "#fff7f7",
        color: "#b91c1c",
        border: "1px solid #fecaca",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 12,
        fontWeight: 600,
      }}
    >
      불러오기 실패
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
      style={{
        height,
        width,
        borderRadius: 6,
        background:
          "linear-gradient(90deg,#f1f5f9 25%,#e2e8f0 37%,#f1f5f9 63%) 0/200% 100%",
        animation: "shimmer 1.4s linear infinite",
      }}
    />
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

function DenyOwnerOnly({ currentRole }: { currentRole: string }) {
  return (
    <div>
      <PageHeader title="세그먼트 × 채널 매트릭스" />
      <Card>
        <div
          style={{
            padding: 32,
            textAlign: "center",
            color: "#475569",
          }}
        >
          <div style={{ fontSize: 32, marginBottom: 8 }}>🔒</div>
          <h3 style={{ fontSize: 16, margin: "0 0 8px" }}>
            OWNER 권한이 필요합니다
          </h3>
          <p style={{ fontSize: 13, color: "#64748b", margin: 0 }}>
            세그먼트 × 채널 매트릭스는 프로젝트 OWNER만 조회할 수 있습니다.
            (현재 권한: <Tag>{currentRole}</Tag>)
          </p>
        </div>
      </Card>
    </div>
  );
}
