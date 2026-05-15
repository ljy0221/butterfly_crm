import { Button, Card, Icon, Tag } from "@/components/common";
import { useLogDetailQuery } from "@/hooks/queries/notification";
import { mapNotificationError } from "@/lib/notification-errors";
import {
  CHANNEL_LABEL,
  STATUS_LABEL,
  STATUS_TONE,
  maskRecipient,
  shortErrorCode,
} from "./notification-status";

/**
 * Sprint 7 — 발송 로그 상세 모달.
 *
 * - 행 클릭 시 openModal 로 마운트.
 * - errorCode 가 있으면 빨간 배너로 에러메시지/코드 강조.
 * - recipient 는 채널별 마스킹.
 * - userAgent / createdAt 등 메타도 mono 폰트로 노출.
 */

interface Props {
  projectId: number;
  logId: number;
  close: () => void;
}

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

export default function NotificationLogDetailModal({
  projectId,
  logId,
  close,
}: Props) {
  const { data, isLoading, isError, error } = useLogDetailQuery(
    projectId,
    logId,
    true,
  );

  const errMsg = isError ? mapNotificationError(error).message : null;

  return (
    <div
      style={{
        width: 640,
        maxWidth: "90vw",
        background: "#fff",
        borderRadius: 14,
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
      }}
    >
      <div
        style={{
          padding: "18px 22px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div>
          <div
            style={{
              fontSize: 17,
              fontWeight: 700,
              color: "#1f2937",
              fontFamily: "var(--font-display)",
            }}
          >
            발송 로그 상세
          </div>
          <div
            style={{
              fontSize: 12,
              color: "#64748b",
              marginTop: 2,
              fontFamily: "var(--font-mono)",
            }}
          >
            #{logId}
          </div>
        </div>
        <button
          type="button"
          onClick={close}
          aria-label="닫기"
          style={{
            border: 0,
            background: "transparent",
            color: "#94a3b8",
            cursor: "pointer",
          }}
        >
          {Icon.x(16)}
        </button>
      </div>

      <div
        style={{
          padding: 22,
          display: "flex",
          flexDirection: "column",
          gap: 14,
          maxHeight: "70vh",
          overflow: "auto",
        }}
      >
        {isLoading && (
          <div style={{ color: "#94a3b8", fontSize: 13, padding: 16 }}>
            로그를 불러오는 중...
          </div>
        )}
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
            }}
          >
            {errMsg}
          </div>
        )}
        {data && (
          <>
            {data.errorCode && (
              <div
                role="alert"
                style={{
                  padding: 12,
                  background: "rgba(239,68,68,.08)",
                  border: "1px solid #fecaca",
                  borderRadius: 8,
                  color: "#b91c1c",
                  fontSize: 12,
                  display: "flex",
                  flexDirection: "column",
                  gap: 4,
                }}
              >
                <div style={{ fontWeight: 700 }}>
                  발송 실패 · {shortErrorCode(data.errorCode)}
                </div>
                <div style={{ fontFamily: "var(--font-mono)" }}>
                  {data.errorMessage ?? "(메시지 없음)"}
                </div>
              </div>
            )}

            <Card title="기본" style={{}}>
              <DetailGrid
                rows={[
                  ["상태", <StatusTag key="s" status={data.status} />],
                  [
                    "채널",
                    <span key="c" style={{ fontWeight: 600 }}>
                      {CHANNEL_LABEL[data.channel ?? ""] ?? data.channel ?? "-"}
                    </span>,
                  ],
                  [
                    "수신자",
                    <span
                      key="r"
                      style={{
                        fontFamily: "var(--font-mono)",
                        wordBreak: "break-all",
                      }}
                    >
                      {maskRecipient(data.recipient, data.channel)}
                    </span>,
                  ],
                ]}
              />
            </Card>

            <Card title="타임라인" style={{}}>
              <DetailGrid
                rows={[
                  [
                    "생성",
                    <TimeCell key="cr" iso={data.createdAt} />,
                  ],
                  [
                    "발송",
                    <TimeCell key="se" iso={data.sentAt} />,
                  ],
                  [
                    "전달",
                    <TimeCell key="de" iso={data.deliveredAt} />,
                  ],
                  [
                    "클릭",
                    <TimeCell key="cl" iso={data.clickedAt} />,
                  ],
                ]}
              />
            </Card>
          </>
        )}
      </div>

      <div
        style={{
          padding: "12px 22px",
          borderTop: "1px solid #f1f5f9",
          display: "flex",
          justifyContent: "flex-end",
          gap: 8,
        }}
      >
        <Button variant="ghost" size="md" onClick={close} style={{}}>
          닫기
        </Button>
      </div>
    </div>
  );
}

function StatusTag({ status }: { status: import("@/schemas/notification").NotificationStatus }) {
  return (
    <Tag tone={STATUS_TONE[status]} dot style={{}}>
      {STATUS_LABEL[status]}
    </Tag>
  );
}

function TimeCell({ iso }: { iso: string | null | undefined }) {
  if (!iso) return <span style={{ color: "#cbd5e1" }}>—</span>;
  return (
    <time
      dateTime={iso}
      style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "#1f2937" }}
    >
      {fmtDateTime(iso)}
    </time>
  );
}

function DetailGrid({
  rows,
}: {
  rows: Array<[string, React.ReactNode]>;
}) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "120px 1fr",
        rowGap: 10,
        columnGap: 16,
        fontSize: 13,
      }}
    >
      {rows.map(([label, value], i) => (
        <div key={i} style={{ display: "contents" }}>
          <div style={{ color: "#64748b" }}>{label}</div>
          <div style={{ color: "#1f2937", wordBreak: "break-word" }}>
            {value}
          </div>
        </div>
      ))}
    </div>
  );
}
