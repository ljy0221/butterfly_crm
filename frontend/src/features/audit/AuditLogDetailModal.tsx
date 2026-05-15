import { Button, Card, Icon, Tag } from "@/components/common";
import { toast } from "@/lib/ui-bus";
import { mapAuditError } from "@/lib/audit-errors";
import { useAuditLogDetailQuery } from "@/hooks/queries/audit";

/**
 * Sprint 7 — 감사 로그 상세 모달.
 *
 * - detail JSON pretty (mono).
 * - userAgent / ipAddress 시맨틱 (<time dateTime>) 노출.
 * - "복사" 버튼만 제공 (수정/삭제 액션 없음 — @Immutable).
 */

interface Props {
  auditId: number;
  close: () => void;
}

function fmtDateTime(iso: string): string {
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

export default function AuditLogDetailModal({ auditId, close }: Props) {
  const { data, isLoading, isError, error } = useAuditLogDetailQuery(
    auditId,
    true,
  );
  const errMsg = isError ? mapAuditError(error).message : null;

  const onCopy = () => {
    if (!data) return;
    const json = JSON.stringify(data, null, 2);
    if (navigator.clipboard) {
      navigator.clipboard.writeText(json);
      toast("감사 로그 JSON을 복사했습니다", "success");
    }
  };

  return (
    <div
      style={{
        width: 720,
        maxWidth: "92vw",
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
            감사 로그 상세
          </div>
          <div
            style={{
              fontSize: 12,
              color: "#64748b",
              marginTop: 2,
              fontFamily: "var(--font-mono)",
            }}
          >
            #{auditId} · append-only · 수정/삭제 불가
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
            감사 로그를 불러오는 중...
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
            <Card title="요약" style={{}}>
              <DetailGrid
                rows={[
                  [
                    "시각",
                    <time
                      key="t"
                      dateTime={data.createdAt}
                      style={{
                        fontFamily: "var(--font-mono)",
                        color: "#1f2937",
                      }}
                    >
                      {fmtDateTime(data.createdAt)}
                    </time>,
                  ],
                  [
                    "멤버",
                    <span
                      key="m"
                      style={{
                        display: "inline-flex",
                        alignItems: "baseline",
                        flexWrap: "wrap",
                        columnGap: 6,
                        rowGap: 2,
                      }}
                    >
                      {data.memberName ? (
                        <>
                          <span style={{ color: "#1f2937", fontWeight: 600 }}>
                            {data.memberName}
                          </span>
                          {data.memberEmail && (
                            <span style={{ color: "#475569", fontSize: 12 }}>
                              {data.memberEmail}
                            </span>
                          )}
                          <span
                            style={{
                              fontFamily: "var(--font-mono)",
                              color: "#94a3b8",
                              fontSize: 11,
                            }}
                          >
                            #{data.memberId}
                          </span>
                        </>
                      ) : (
                        <>
                          <span
                            style={{
                              fontFamily: "var(--font-mono)",
                              color: "#1f2937",
                            }}
                          >
                            #{data.memberId}
                          </span>
                          <Tag tone="info" style={{}}>
                            탈퇴/식별 불가
                          </Tag>
                        </>
                      )}
                    </span>,
                  ],
                  [
                    "프로젝트",
                    data.projectId == null ? (
                      <Tag key="p" tone="info" style={{}}>
                        시스템
                      </Tag>
                    ) : (
                      <span
                        style={{ fontFamily: "var(--font-mono)", color: "#1f2937" }}
                      >{`프로젝트 #${data.projectId}`}</span>
                    ),
                  ],
                  [
                    "action",
                    <Tag key="a" tone="warning" style={{}}>
                      {data.action}
                    </Tag>,
                  ],
                  [
                    "대상",
                    <span
                      key="tgt"
                      style={{
                        display: "inline-flex",
                        alignItems: "baseline",
                        gap: 6,
                      }}
                    >
                      <Tag tone="info" style={{}}>
                        {data.targetType}
                      </Tag>
                      {data.targetId != null && (
                        <span
                          style={{
                            fontFamily: "var(--font-mono)",
                            color: "#94a3b8",
                            fontSize: 11,
                          }}
                        >
                          #{data.targetId}
                        </span>
                      )}
                    </span>,
                  ],
                  [
                    "IP",
                    <span
                      key="ip"
                      style={{ fontFamily: "var(--font-mono)", color: "#64748b" }}
                    >
                      {data.ipAddress ?? "-"}
                    </span>,
                  ],
                  [
                    "User Agent",
                    <span
                      key="ua"
                      style={{
                        fontFamily: "var(--font-mono)",
                        color: "#64748b",
                        fontSize: 11,
                        wordBreak: "break-all",
                      }}
                    >
                      {data.userAgent ?? "-"}
                    </span>,
                  ],
                ]}
              />
            </Card>

            <Card title="detail (JSON)" style={{}}>
              <pre
                style={{
                  margin: 0,
                  padding: 14,
                  background: "#0f172a",
                  color: "#e2e8f0",
                  borderRadius: 8,
                  fontFamily: "var(--font-mono)",
                  fontSize: 11,
                  lineHeight: 1.6,
                  overflow: "auto",
                  maxHeight: 280,
                }}
              >
                {JSON.stringify(data.detail ?? null, null, 2)}
              </pre>
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
        <Button
          variant="secondary"
          size="md"
          icon={Icon.copy ? Icon.copy(12) : undefined}
          onClick={onCopy}
          disabled={!data}
          style={{}}
        >
          JSON 복사
        </Button>
        <Button variant="ghost" size="md" onClick={close} style={{}}>
          닫기
        </Button>
      </div>
    </div>
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
        gridTemplateColumns: "100px 1fr",
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
