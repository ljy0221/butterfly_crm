import { useMemo } from "react";
import { Card, Tag, Avatar } from "@/components/common";
import { useMembersQuery, useProjectQuery } from "@/hooks/queries/tenant";
import { mapTenantError } from "@/lib/tenant-errors";

/**
 * Sprint 2 — 프로젝트 정보 탭(VIEWER 이상 조회).
 * 백엔드 PUT 엔드포인트 미제공 → 표시 전용.
 */
export default function ProjectInfoTab({ projectId }: { projectId: number }) {
  const projectQ = useProjectQuery(projectId);
  const membersQ = useMembersQuery(projectId);

  const owner = useMemo(() => {
    if (!projectQ.data || !membersQ.data) return null;
    return (
      membersQ.data.find((m) => m.memberId === projectQ.data?.ownerId) ?? null
    );
  }, [projectQ.data, membersQ.data]);

  if (projectQ.isError) {
    const { message } = mapTenantError(projectQ.error);
    return (
      <Card title="프로젝트 정보">
        <ErrorState message={message} />
      </Card>
    );
  }

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "minmax(0,1fr) 320px",
        gap: 16,
      }}
    >
      <Card title="프로젝트 정보">
        {projectQ.isLoading || !projectQ.data ? (
          <KvSkeleton rows={6} />
        ) : (
          <dl style={{ margin: 0 }}>
            <KvRow label="프로젝트 ID" value={`#${projectQ.data.projectId}`} mono />
            <KvRow label="프로젝트명" value={projectQ.data.name} />
            <KvRow
              label="사업자번호"
              value={formatBusinessNo(projectQ.data.businessNo)}
              mono
            />
            <KvRow
              label="회사 인증번호"
              value={projectQ.data.companyAuthNo}
              mono
            />
            <KvRow
              label="플랜"
              value={
                <span>
                  <Tag tone="info">{projectQ.data.plan}</Tag>
                  <span
                    style={{
                      marginLeft: 8,
                      fontSize: 12,
                      color: "var(--fg-secondary, #64748b)",
                    }}
                  >
                    일 한도{" "}
                    {projectQ.data.dailyLimit != null
                      ? projectQ.data.dailyLimit.toLocaleString()
                      : "-"}
                    건
                  </span>
                </span>
              }
            />
            <KvRow
              label="API 키"
              value={
                projectQ.data.hasApiKey ? (
                  <Tag tone="success" dot>
                    발급됨
                  </Tag>
                ) : (
                  <Tag tone="default" dot>
                    미발급
                  </Tag>
                )
              }
            />
          </dl>
        )}
      </Card>

      <Card title="소유자">
        {projectQ.isLoading || membersQ.isLoading ? (
          <OwnerSkeleton />
        ) : owner ? (
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <Avatar name={owner.name} size={40} />
            <div style={{ minWidth: 0 }}>
              <div
                style={{
                  fontWeight: 600,
                  color: "#1f2937",
                  fontSize: 14,
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
              >
                {owner.name}
              </div>
              <div
                style={{
                  fontSize: 12,
                  color: "#64748b",
                  whiteSpace: "nowrap",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
              >
                {owner.email}
              </div>
              <div
                style={{
                  fontSize: 11,
                  color: "#94a3b8",
                  marginTop: 2,
                  fontFamily: "var(--font-mono)",
                }}
              >
                memberId #{owner.memberId}
              </div>
            </div>
          </div>
        ) : (
          <div style={{ fontSize: 12, color: "#94a3b8" }}>
            소유자 정보를 불러올 수 없습니다.
          </div>
        )}
      </Card>
    </div>
  );
}

function KvRow({
  label,
  value,
  mono,
}: {
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "140px 1fr",
        padding: "10px 0",
        borderBottom: "1px solid #f1f5f9",
        alignItems: "center",
      }}
    >
      <dt
        style={{
          margin: 0,
          fontSize: 12,
          color: "#64748b",
          fontWeight: 500,
        }}
      >
        {label}
      </dt>
      <dd
        style={{
          margin: 0,
          fontSize: 13,
          color: "#1f2937",
          fontFamily: mono ? "var(--font-mono)" : "inherit",
          wordBreak: "break-all",
        }}
      >
        {value}
      </dd>
    </div>
  );
}

function KvSkeleton({ rows }: { rows: number }) {
  return (
    <div>
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          style={{
            height: 40,
            borderBottom: "1px solid #f1f5f9",
            display: "flex",
            alignItems: "center",
            gap: 12,
          }}
        >
          <div
            style={{
              width: 100,
              height: 10,
              borderRadius: 4,
              background: "#f1f5f9",
            }}
          />
          <div
            style={{
              flex: 1,
              height: 10,
              borderRadius: 4,
              background: "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
              backgroundSize: "200% 100%",
              animation: "shimmer 1.4s infinite",
            }}
          />
        </div>
      ))}
      <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>
    </div>
  );
}

function OwnerSkeleton() {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
      <div
        style={{
          width: 40,
          height: 40,
          borderRadius: 999,
          background: "#f1f5f9",
        }}
      />
      <div style={{ flex: 1 }}>
        <div
          style={{
            height: 12,
            width: "60%",
            background: "#f1f5f9",
            borderRadius: 4,
            marginBottom: 6,
          }}
        />
        <div
          style={{
            height: 10,
            width: "80%",
            background: "#f1f5f9",
            borderRadius: 4,
          }}
        />
      </div>
    </div>
  );
}

function ErrorState({ message }: { message: string }) {
  return (
    <div
      style={{
        padding: 16,
        background: "rgba(239,68,68,.06)",
        border: "1px solid rgba(239,68,68,.2)",
        borderRadius: 8,
        color: "#b91c1c",
        fontSize: 13,
      }}
    >
      {message}
    </div>
  );
}

/** "1234567890" → "123-45-67890". 10자리가 아니면 그대로 반환. */
function formatBusinessNo(v: string): string {
  const d = v?.replace(/\D/g, "") ?? "";
  if (d.length !== 10) return v;
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`;
}
