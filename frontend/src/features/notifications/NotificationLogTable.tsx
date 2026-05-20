import { useMemo, useState } from "react";
import { Button, Card, Icon, Select, Tag } from "@/components/common";
import { openModal } from "@/lib/ui-bus";
import { mapNotificationError } from "@/lib/notification-errors";
import { useCampaignLogsQuery } from "@/hooks/queries/notification";
import type {
  NotificationChannel,
  NotificationStatus,
} from "@/schemas/notification";
import type { CampaignLogsParams } from "@/api/notification";
import {
  CHANNEL_LABEL,
  STATUS_LABEL,
  STATUS_TONE,
} from "./notification-status";
import NotificationLogDetailModal from "./NotificationLogDetailModal";

/**
 * Sprint 7 — 발송 로그 테이블. 캠페인 상세 안 ?tab=logs 에 마운트.
 *
 * 필터: 채널 ▾ | 상태 ▾ | 페이지 사이즈
 * 행 클릭(또는 Enter/Space) → openModal(NotificationLogDetailModal).
 */

interface Props {
  projectId: number;
  campaignId: number;
  campaignStatus?: string; // DRAFT 면 빈 안내 변경
}

const CHANNEL_OPTIONS: Array<{ value: "" | NotificationChannel; label: string }> = [
  { value: "", label: "전체 채널" },
  { value: "PUSH", label: "PUSH" },
  { value: "EMAIL", label: "EMAIL" },
  { value: "SMS", label: "SMS" },
  { value: "KAKAO", label: "KAKAO" },
  { value: "WEBHOOK", label: "WEBHOOK" },
];

const STATUS_OPTIONS: Array<{ value: "" | NotificationStatus; label: string }> = [
  { value: "", label: "전체 상태" },
  { value: "DELIVERED", label: "전달" },
  { value: "CLICKED", label: "클릭" },
  { value: "FAILED", label: "실패" },
];

const PAGE_SIZE = 20;

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

export default function NotificationLogTable({
  projectId,
  campaignId,
  campaignStatus,
}: Props) {
  const [channel, setChannel] = useState<"" | NotificationChannel>("");
  const [status, setStatus] = useState<"" | NotificationStatus>("");
  const [page, setPage] = useState(0);

  const params = useMemo<CampaignLogsParams>(
    () => ({
      channel: channel || undefined,
      status: status || undefined,
      page,
      size: PAGE_SIZE,
    }),
    [channel, status, page],
  );

  const isActive = campaignStatus === "ACTIVE";

  const { data, isLoading, isError, error, refetch } = useCampaignLogsQuery(
    projectId,
    campaignId,
    params,
    true,
    isActive ? 10_000 : false,
  );

  const onRowOpen = (logId: number) => {
    openModal(({ close }) => (
      <NotificationLogDetailModal
        projectId={projectId}
        logId={logId}
        close={close}
      />
    ));
  };

  const errMsg = isError ? mapNotificationError(error).message : null;
  const isDraft = campaignStatus === "DRAFT";

  return (
    <Card
      title="발송 로그"
      subtitle="캠페인 발송 이력 · 행을 클릭하면 상세를 확인할 수 있습니다"
      style={{}}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          marginBottom: 12,
          flexWrap: "wrap",
        }}
      >
        <Select
          value={channel}
          onChange={(v: string) => {
            setChannel(v as "" | NotificationChannel);
            setPage(0);
          }}
          options={CHANNEL_OPTIONS}
          width={140}
          aria-label="채널 필터"
        />
        <Select
          value={status}
          onChange={(v: string) => {
            setStatus(v as "" | NotificationStatus);
            setPage(0);
          }}
          options={STATUS_OPTIONS}
          width={140}
          aria-label="상태 필터"
        />
        <div style={{ marginLeft: "auto", fontSize: 11, color: "#94a3b8" }}>
          총 {data?.totalElements ?? 0}건
        </div>
      </div>

      {errMsg && (
        <div
          role="alert"
          style={{
            padding: 12,
            background: "rgba(239,68,68,.08)",
            border: "1px solid #fecaca",
            borderRadius: 8,
            color: "#b91c1c",
            fontSize: 13,
            marginBottom: 12,
          }}
        >
          {errMsg}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => refetch()}
            style={{ marginLeft: 12 }}
          >
            재시도
          </Button>
        </div>
      )}

      <div
        style={{
          border: "1px solid #e2e8f0",
          borderRadius: 10,
          overflow: "hidden",
        }}
      >
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "0.6fr 0.7fr 0.7fr 0.7fr 0.9fr 0.9fr 0.9fr 1fr",
            padding: "12px 16px",
            background: "#f8fafc",
            borderBottom: "1px solid #e2e8f0",
            fontSize: 11,
            color: "#64748b",
            fontWeight: 600,
          }}
        >
          <div>로그 ID</div>
          <div>userId</div>
          <div>채널</div>
          <div>상태</div>
          <div>발송</div>
          <div>전달</div>
          <div>클릭</div>
          <div>오류</div>
        </div>
        {isLoading && (
          <div
            style={{
              padding: 24,
              textAlign: "center",
              color: "#94a3b8",
              fontSize: 13,
            }}
          >
            로그를 불러오는 중...
          </div>
        )}
        {!isLoading && (data?.content?.length ?? 0) === 0 && (
          <div
            style={{
              padding: 32,
              textAlign: "center",
              color: "#94a3b8",
              fontSize: 13,
            }}
          >
            {isDraft
              ? "이 캠페인은 아직 시작 전입니다 — 캠페인을 시작하면 로그가 쌓입니다."
              : "이 캠페인에 발송 이력이 없습니다."}
          </div>
        )}
        {data?.content?.map((row, i, arr) => (
          <div
            key={row.logId}
            role="button"
            tabIndex={0}
            onClick={() => onRowOpen(row.logId)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onRowOpen(row.logId);
              }
            }}
            style={{
              display: "grid",
              gridTemplateColumns:
                "0.6fr 0.7fr 0.7fr 0.7fr 0.9fr 0.9fr 0.9fr 1fr",
              padding: "12px 16px",
              alignItems: "center",
              fontSize: 12,
              cursor: "pointer",
              borderBottom:
                i < arr.length - 1 ? "1px solid #f1f5f9" : "none",
              outline: "none",
              transition: "background 120ms",
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = "#f8fafc";
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = "#fff";
            }}
            onFocus={(e) => {
              e.currentTarget.style.background = "#eff3fe";
            }}
            onBlur={(e) => {
              e.currentTarget.style.background = "#fff";
            }}
          >
            <div
              style={{
                fontFamily: "var(--font-mono)",
                color: "#3b5bdb",
                fontWeight: 600,
              }}
            >
              #{row.logId}
            </div>
            <div style={{ fontFamily: "var(--font-mono)", color: "#1f2937" }}>
              {row.userId}
            </div>
            <div>
              <Tag tone="default" style={{}}>
                {CHANNEL_LABEL[row.channel ?? ""] ?? row.channel ?? "-"}
              </Tag>
            </div>
            <div>
              <Tag tone={STATUS_TONE[row.status]} dot style={{}}>
                {STATUS_LABEL[row.status]}
              </Tag>
            </div>
            <TimeCell iso={row.sentAt} />
            <TimeCell iso={row.deliveredAt} />
            <TimeCell iso={row.clickedAt} />
            <div
              style={{
                color: row.status === "FAILED" ? "#b91c1c" : "#94a3b8",
                fontSize: 11,
              }}
            >
              {row.status === "FAILED" ? "오류 (상세 보기)" : "—"}
            </div>
          </div>
        ))}
      </div>

      {data && data.totalPages > 1 && (
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            marginTop: 12,
            fontSize: 12,
            color: "#64748b",
          }}
        >
          <div>
            {data.totalElements}건 · 페이지 {data.page + 1} / {data.totalPages}
          </div>
          <div style={{ display: "flex", gap: 6 }}>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
              icon={Icon.chevron ? Icon.chevron(12) : undefined}
              style={{}}
            >
              이전
            </Button>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setPage(page + 1)}
              disabled={page >= data.totalPages - 1}
              style={{}}
            >
              다음
            </Button>
          </div>
        </div>
      )}
    </Card>
  );
}

function TimeCell({ iso }: { iso: string | null | undefined }) {
  if (!iso) {
    return <span style={{ color: "#cbd5e1" }}>—</span>;
  }
  return (
    <time
      dateTime={iso}
      style={{ fontFamily: "var(--font-mono)", color: "#64748b", fontSize: 11 }}
    >
      {fmtDateTime(iso)}
    </time>
  );
}
