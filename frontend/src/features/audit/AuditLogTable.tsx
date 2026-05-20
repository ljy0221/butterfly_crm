import { useMemo, useState } from "react";
import {
  Button,
  Card,
  Icon,
  Input,
  Select,
  Tag,
} from "@/components/common";
import { openModal } from "@/lib/ui-bus";
import { mapAuditError } from "@/lib/audit-errors";
import { useAuditLogsQuery } from "@/hooks/queries/audit";
import type { AuditLogListParams } from "@/api/audit";
import { exportAuditLogsCsv } from "@/api/audit";
import AuditLogDetailModal from "./AuditLogDetailModal";

/**
 * Sprint 7 — 감사 로그 테이블 (두 진입점에서 공유).
 *
 * scope:
 *  - "project": SettingsLayout ?tab=audit. projectId 고정, projectId 필터 숨김.
 *  - "admin":   /admin/audit. projectId 입력 필드 노출 (없으면 전체).
 *
 * @Immutable 안내 카드 헤더에 명시. 수정/삭제 액션 UI 없음.
 */

interface Props {
  scope: "project" | "admin";
  /** scope === "project" 일 때 필수. scope === "admin" 에서는 무시. */
  projectId?: number;
}

const ACTION_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "", label: "전체 action" },
  { value: "CREATE", label: "CREATE" },
  { value: "UPDATE", label: "UPDATE" },
  { value: "DELETE", label: "DELETE" },
  { value: "READ", label: "READ" },
  { value: "EXPORT", label: "EXPORT" },
  { value: "LOGIN", label: "LOGIN" },
  { value: "ROLE_CHANGE", label: "ROLE_CHANGE" },
];

const TARGET_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "", label: "전체 대상" },
  { value: "CAMPAIGN", label: "CAMPAIGN" },
  { value: "SEGMENT", label: "SEGMENT" },
  { value: "TEMPLATE", label: "TEMPLATE" },
  { value: "MEMBER", label: "MEMBER" },
  { value: "API_KEY", label: "API_KEY" },
  { value: "CREDENTIAL", label: "CREDENTIAL" },
  { value: "PROJECT", label: "PROJECT" },
];

const PAGE_SIZE = 20;

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

export default function AuditLogTable({ scope, projectId }: Props) {
  const [action, setAction] = useState("");
  const [targetType, setTargetType] = useState("");
  const [memberIdInput, setMemberIdInput] = useState("");
  const [adminProjectIdInput, setAdminProjectIdInput] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [page, setPage] = useState(0);
  const [csvLoading, setCsvLoading] = useState(false);

  const memberId = memberIdInput.trim() ? Number(memberIdInput.trim()) : undefined;
  const adminProjectId = adminProjectIdInput.trim()
    ? Number(adminProjectIdInput.trim())
    : undefined;

  const params = useMemo<AuditLogListParams>(() => {
    const p: AuditLogListParams = {
      action: action || undefined,
      targetType: targetType || undefined,
      memberId: memberId && Number.isFinite(memberId) ? memberId : undefined,
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      page,
      size: PAGE_SIZE,
    };
    if (scope === "project") {
      p.projectId = projectId;
    } else if (adminProjectId && Number.isFinite(adminProjectId)) {
      p.projectId = adminProjectId;
    }
    return p;
  }, [
    scope,
    projectId,
    action,
    targetType,
    memberId,
    adminProjectId,
    startDate,
    endDate,
    page,
  ]);

  const { data, isLoading, isError, error, refetch } = useAuditLogsQuery(
    params,
    scope === "admin" || projectId != null,
  );

  const errMsg = isError ? mapAuditError(error).message : null;

  const onRowOpen = (auditId: number) => {
    openModal(({ close }) => (
      <AuditLogDetailModal auditId={auditId} close={close} />
    ));
  };

  return (
    <Card
      title="감사 로그"
      subtitle="모든 민감 정보 접근 및 중요 작업이 append-only로 기록됩니다 (수정·삭제 불가)"
      style={{}}
    >
      <div
        style={{
          marginBottom: 12,
          padding: 10,
          background: "#fffbeb",
          border: "1px solid #fde68a",
          borderRadius: 8,
          color: "#92400e",
          fontSize: 11,
          display: "flex",
          alignItems: "center",
          gap: 8,
        }}
      >
        <Tag tone="warning" style={{}}>
          @Immutable
        </Tag>
        감사 로그는 한 번 기록되면 수정·삭제할 수 없습니다. 90일 이상 경과한 항목은
        아카이브로 이관될 수 있습니다.
      </div>

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
          value={action}
          onChange={(v: string) => {
            setAction(v);
            setPage(0);
          }}
          options={ACTION_OPTIONS}
          width={150}
          aria-label="action 필터"
        />
        <Select
          value={targetType}
          onChange={(v: string) => {
            setTargetType(v);
            setPage(0);
          }}
          options={TARGET_OPTIONS}
          width={150}
          aria-label="대상 필터"
        />
        <Input
          type="number"
          placeholder="memberId"
          value={memberIdInput}
          onChange={(e: { target: { value: string } }) => {
            setMemberIdInput(e.target.value);
            setPage(0);
          }}
          width={120}
          aria-label="memberId 필터"
        />
        {scope === "admin" && (
          <Input
            type="number"
            placeholder="projectId"
            value={adminProjectIdInput}
            onChange={(e: { target: { value: string } }) => {
              setAdminProjectIdInput(e.target.value);
              setPage(0);
            }}
            width={120}
            aria-label="projectId 필터"
          />
        )}
        <Input
          type="date"
          value={startDate}
          onChange={(e: { target: { value: string } }) => {
            setStartDate(e.target.value);
            setPage(0);
          }}
          width={150}
          aria-label="시작일"
        />
        <Input
          type="date"
          value={endDate}
          onChange={(e: { target: { value: string } }) => {
            setEndDate(e.target.value);
            setPage(0);
          }}
          width={150}
          aria-label="종료일"
        />
        <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 10 }}>
          <span style={{ fontSize: 11, color: "#94a3b8" }}>
            총 {data?.totalElements ?? 0}건
          </span>
          <Button
            variant="secondary"
            size="sm"
            disabled={csvLoading}
            onClick={async () => {
              setCsvLoading(true);
              try {
                await exportAuditLogsCsv(params);
              } finally {
                setCsvLoading(false);
              }
            }}
          >
            {csvLoading ? "다운로드 중..." : "CSV 내보내기"}
          </Button>
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
            gridTemplateColumns: scope === "admin"
              ? "1.4fr 0.6fr 0.7fr 0.8fr 1fr 1fr"
              : "1.4fr 0.7fr 0.8fr 1fr 1fr",
            padding: "12px 16px",
            background: "#f8fafc",
            borderBottom: "1px solid #e2e8f0",
            fontSize: 11,
            color: "#64748b",
            fontWeight: 600,
          }}
        >
          <div>시각</div>
          {scope === "admin" && <div>프로젝트</div>}
          <div>멤버</div>
          <div>action</div>
          <div>대상</div>
          <div>IP</div>
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
            감사 로그를 불러오는 중...
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
            선택 조건에 해당하는 감사 로그가 없습니다.
          </div>
        )}
        {data?.content?.map((row, i, arr) => (
          <div
            key={row.auditId}
            role="button"
            tabIndex={0}
            onClick={() => onRowOpen(row.auditId)}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onRowOpen(row.auditId);
              }
            }}
            style={{
              display: "grid",
              gridTemplateColumns: scope === "admin"
                ? "1.4fr 0.6fr 0.7fr 0.8fr 1fr 1fr"
                : "1.4fr 0.7fr 0.8fr 1fr 1fr",
              padding: "12px 16px",
              alignItems: "center",
              fontSize: 12,
              cursor: "pointer",
              borderBottom:
                i < arr.length - 1 ? "1px solid #f1f5f9" : "none",
              outline: "none",
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
            <div>
              <time
                dateTime={row.createdAt}
                style={{
                  fontFamily: "var(--font-mono)",
                  color: "#64748b",
                  fontSize: 11,
                }}
              >
                {fmtDateTime(row.createdAt)}
              </time>
            </div>
            {scope === "admin" && (
              <div style={{ color: "#1f2937", fontSize: 12 }}>
                {row.projectId == null ? (
                  <Tag tone="info" style={{}}>
                    시스템
                  </Tag>
                ) : (
                  <span style={{ fontFamily: "var(--font-mono)" }}>
                    프로젝트 #{row.projectId}
                  </span>
                )}
              </div>
            )}
            <div style={{ color: "#1f2937" }}>
              {row.memberName ? (
                <>
                  <span style={{ fontWeight: 600 }}>{row.memberName}</span>
                  <span
                    style={{
                      marginLeft: 6,
                      color: "#94a3b8",
                      fontFamily: "var(--font-mono)",
                      fontSize: 11,
                    }}
                  >
                    #{row.memberId}
                  </span>
                </>
              ) : (
                <span style={{ fontFamily: "var(--font-mono)" }}>
                  #{row.memberId}
                </span>
              )}
            </div>
            <div>
              <Tag tone="warning" style={{}}>
                {row.action}
              </Tag>
            </div>
            <div style={{ display: "inline-flex", alignItems: "baseline", gap: 6 }}>
              <Tag tone="info" style={{}}>
                {row.targetType}
              </Tag>
              {row.targetId != null && (
                <span
                  style={{
                    color: "#94a3b8",
                    fontFamily: "var(--font-mono)",
                    fontSize: 11,
                  }}
                >
                  #{row.targetId}
                </span>
              )}
            </div>
            <div
              style={{
                fontFamily: "var(--font-mono)",
                color: "#94a3b8",
                fontSize: 11,
              }}
            >
              {row.ipAddress ?? "-"}
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

void Icon; // suppress unused warning when Icon namespace import
