import { useMemo, useState } from "react";
import {
  Button,
  Card,
  PageHeader,
  Tag,
} from "@/components/common";
import { openModal } from "@/lib/ui-bus";
import {
  useApplicationsQuery,
} from "@/hooks/queries/application";
import type {
  ApplicationResponse,
  ApplicationStatus,
} from "@/api/application";
import ApplicationDetailModal from "./ApplicationDetailModal";

const STATUS_TABS: Array<{ key: ApplicationStatus | "ALL"; label: string }> = [
  { key: "ALL", label: "전체" },
  { key: "PENDING", label: "대기중" },
  { key: "APPROVED", label: "승인" },
  { key: "REJECTED", label: "반려" },
];

const STATUS_TONE: Record<ApplicationStatus, string> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "error",
};

const STATUS_LABEL: Record<ApplicationStatus, string> = {
  PENDING: "대기",
  APPROVED: "승인",
  REJECTED: "반려",
};

export default function AdminApplications() {
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | "ALL">(
    "ALL",
  );
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = useApplicationsQuery({
    status: statusFilter === "ALL" ? undefined : statusFilter,
    page,
    size: 20,
  });

  const totalPages = data?.totalPages ?? 0;
  const items = useMemo<ApplicationResponse[]>(() => data?.content ?? [], [data]);

  const openDetail = (app: ApplicationResponse) => {
    openModal(({ close }: { close: () => void }) => (
      <ApplicationDetailModal application={app} onClose={close} />
    ));
  };

  return (
    <div>
      <PageHeader
        title="신청서 승인"
        subtitle="대기 중인 가입 신청을 검토하고 승인/반려합니다"
      />

      <div
        style={{
          display: "flex",
          gap: 6,
          margin: "8px 0 16px",
        }}
      >
        {STATUS_TABS.map((t) => {
          const active = statusFilter === t.key;
          return (
            <button
              key={t.key}
              onClick={() => {
                setStatusFilter(t.key);
                setPage(0);
              }}
              style={{
                padding: "6px 12px",
                fontSize: 12,
                fontFamily: "inherit",
                background: active
                  ? "var(--color-primary-strong)"
                  : "var(--bg-surface)",
                color: active ? "#fff" : "var(--fg-primary)",
                border: active
                  ? "1px solid var(--color-primary-strong)"
                  : "1px solid var(--border-default)",
                borderRadius: "var(--radius-sm)",
                cursor: "pointer",
              }}
            >
              {t.label}
            </button>
          );
        })}
      </div>

      <Card padding={0} title={null}>
        <table
          style={{
            width: "100%",
            borderCollapse: "collapse",
            fontSize: 13,
          }}
        >
          <thead>
            <tr style={{ background: "var(--bg-alt)" }}>
              <th style={th()}>신청자</th>
              <th style={th()}>이메일</th>
              <th style={th()}>사업자명</th>
              <th style={th()}>사업자번호</th>
              <th style={th()}>상태</th>
              <th style={th()}>신청일</th>
            </tr>
          </thead>
          <tbody>
            {isLoading &&
              Array.from({ length: 6 }).map((_, i) => (
                <tr key={i}>
                  <td colSpan={6} style={{ padding: 0 }}>
                    <div
                      style={{
                        height: 44,
                        margin: "0 12px",
                        borderBottom: "1px solid var(--border-default)",
                        background:
                          "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
                        backgroundSize: "200% 100%",
                        animation: "shimmer 1.4s infinite",
                      }}
                    />
                  </td>
                </tr>
              ))}
            {isError && (
              <tr>
                <td
                  colSpan={6}
                  style={{
                    textAlign: "center",
                    padding: 32,
                    color: "var(--color-error)",
                  }}
                >
                  목록을 불러오지 못했습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && items.length === 0 && (
              <tr>
                <td
                  colSpan={6}
                  style={{
                    textAlign: "center",
                    padding: 48,
                    color: "var(--fg-secondary)",
                  }}
                >
                  📭 승인 대기 중인 신청서가 없습니다
                </td>
              </tr>
            )}
            {!isLoading &&
              !isError &&
              items.map((app) => (
                <tr
                  key={app.applicationId}
                  tabIndex={0}
                  role="button"
                  aria-label={`신청서 ${app.applicantName} (${app.businessName}) 상세 보기`}
                  onClick={() => openDetail(app)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      openDetail(app);
                    }
                  }}
                  style={{
                    cursor: "pointer",
                    borderBottom: "1px solid var(--border-default)",
                    outline: "none",
                  }}
                  onFocus={(e) =>
                    (e.currentTarget.style.background = "var(--bg-alt)")
                  }
                  onBlur={(e) => (e.currentTarget.style.background = "")}
                >
                  <td style={td()}>{app.applicantName}</td>
                  <td style={td()}>{app.email}</td>
                  <td style={td()}>{app.businessName}</td>
                  <td style={{ ...td(), fontFamily: "var(--font-mono)" }}>
                    {app.businessNo}
                  </td>
                  <td style={td()}>
                    <Tag tone={STATUS_TONE[app.status]} dot style={{}}>
                      {STATUS_LABEL[app.status]}
                    </Tag>
                  </td>
                  <td style={td()}>
                    {app.createdAt?.slice(0, 10) ?? "-"}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>

        <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>
      </Card>

      {/* Pagination */}
      {totalPages > 1 && (
        <div
          style={{
            display: "flex",
            gap: 4,
            justifyContent: "flex-end",
            marginTop: 16,
          }}
        >
          <Button
            variant="ghost"
            size="sm"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            style={{ opacity: page === 0 ? 0.4 : 1 }}
          >
            이전
          </Button>
          <span
            style={{
              fontSize: 12,
              color: "var(--fg-secondary)",
              padding: "6px 8px",
            }}
          >
            {page + 1} / {totalPages}
          </span>
          <Button
            variant="ghost"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            style={{ opacity: page >= totalPages - 1 ? 0.4 : 1 }}
          >
            다음
          </Button>
        </div>
      )}
    </div>
  );
}

function th(): React.CSSProperties {
  return {
    textAlign: "left",
    padding: "10px 12px",
    fontSize: 12,
    color: "var(--fg-secondary)",
    fontWeight: 500,
    borderBottom: "1px solid var(--border-default)",
  };
}

function td(): React.CSSProperties {
  return {
    padding: "12px",
    fontSize: 13,
    color: "var(--fg-primary)",
  };
}
