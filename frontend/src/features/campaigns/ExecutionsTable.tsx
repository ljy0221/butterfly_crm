import { Tag } from "@/components/common";
import type { CampaignExecution } from "@/api/campaign";
import type { ExecutionStatus } from "@/schemas/campaign";

/**
 * Sprint 6 — 캠페인 실행 이력 테이블.
 * mock store 의 CampaignExecutionRecord[] 를 회차/상태/대상/발송/실패/시각/사유 컬럼으로 출력.
 */

const STATUS_LABEL: Record<ExecutionStatus, string> = {
  READY: "대기",
  SEND: "발송 중",
  SUCCESS: "발송 완료",
  FAIL: "실패",
};

const STATUS_TONE: Record<ExecutionStatus, "default" | "info" | "success" | "warning" | "error"> = {
  READY: "default",
  SEND: "info",
  SUCCESS: "success",
  FAIL: "error",
};

function fmtDate(iso: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", {
    year: "2-digit",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export interface ExecutionsTableProps {
  executions: CampaignExecution[];
}

export default function ExecutionsTable({ executions }: ExecutionsTableProps) {
  if (executions.length === 0) {
    return (
      <div
        style={{
          padding: 32,
          textAlign: "center",
          color: "#94a3b8",
          fontSize: 12.5,
          background: "#fff",
          border: "1px dashed #e2e8f0",
          borderRadius: 8,
        }}
      >
        실행 이력이 없습니다. 캠페인을 시작하면 발송 회차가 기록됩니다.
      </div>
    );
  }

  return (
    <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden", background: "#fff" }}>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "0.5fr 1fr 0.8fr 0.8fr 0.8fr 1.2fr 1.4fr",
          padding: "10px 14px",
          background: "#f8fafc",
          borderBottom: "1px solid #e2e8f0",
          fontSize: 11,
          color: "#64748b",
          fontWeight: 600,
        }}
      >
        <div>회차</div>
        <div>상태</div>
        <div style={{ textAlign: "right" }}>대상</div>
        <div style={{ textAlign: "right" }}>발송</div>
        <div style={{ textAlign: "right" }}>실패</div>
        <div>시작 시각</div>
        <div>종료 시각 / 실패사유</div>
      </div>
      {executions.map((ex, i) => (
        <div
          key={ex.campaignExecutionId}
          style={{
            display: "grid",
            gridTemplateColumns: "0.5fr 1fr 0.8fr 0.8fr 0.8fr 1.2fr 1.4fr",
            padding: "12px 14px",
            alignItems: "center",
            fontSize: 12.5,
            borderBottom: i < executions.length - 1 ? "1px solid #f1f5f9" : "none",
          }}
        >
          <div style={{ color: "#1f2937", fontWeight: 600 }}>#{ex.runRound}</div>
          <div>
            <Tag tone={STATUS_TONE[ex.campaignExecutionStatus]} dot style={{}}>
              {STATUS_LABEL[ex.campaignExecutionStatus]}
            </Tag>
          </div>
          <div style={{ textAlign: "right", color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>
            {ex.targetCount.toLocaleString()}
          </div>
          <div style={{ textAlign: "right", color: "#16a34a", fontVariantNumeric: "tabular-nums", fontWeight: 600 }}>
            {ex.sentCount.toLocaleString()}
          </div>
          <div style={{ textAlign: "right", color: ex.failedCount > 0 ? "#ef4444" : "#94a3b8", fontVariantNumeric: "tabular-nums" }}>
            {ex.failedCount.toLocaleString()}
          </div>
          <div style={{ color: "#64748b", fontSize: 11.5 }}>{fmtDate(ex.sendStartedAt)}</div>
          <div style={{ color: ex.failReason ? "#b91c1c" : "#64748b", fontSize: 11.5 }}>
            {ex.failReason ? ex.failReason : fmtDate(ex.sendCompletedAt)}
          </div>
        </div>
      ))}
    </div>
  );
}
