import { useProjectStore } from "@/stores/project";
import { useAuthStore } from "@/stores/auth";
import AuditLogTable from "@/features/audit/AuditLogTable";

/**
 * Sprint 7 — Settings ?tab=audit 래퍼.
 *
 * 권한 가드 인라인:
 *  - OWNER 또는 ADMIN 만 통과.
 *  - 그 외 사용자에게는 안내 카드.
 *
 * scope="project" — projectId 고정.
 */
export default function AuditTab({ projectId }: { projectId: number }) {
  const project = useProjectStore((s) => s.current);
  const member = useAuthStore((s) => s.member);
  const role = member?.role === "ADMIN" ? "ADMIN" : project?.role ?? "VIEWER";
  const allowed = role === "OWNER" || role === "ADMIN";

  if (!allowed) {
    return (
      <div
        role="alert"
        style={{
          padding: 32,
          textAlign: "center",
          background: "#fff",
          border: "1px solid var(--border-default, #e2e8f0)",
          borderRadius: 12,
          color: "#64748b",
          fontSize: 13,
          lineHeight: 1.6,
        }}
      >
        <div style={{ fontSize: 28, marginBottom: 8 }}>🔒</div>
        <div style={{ fontSize: 16, fontWeight: 700, color: "#1f2937" }}>
          감사 로그 열람은 OWNER 이상 권한이 필요합니다
        </div>
        <div style={{ marginTop: 6 }}>
          현재 권한: {role}. 프로젝트 OWNER 또는 시스템 ADMIN 에게 요청하세요.
        </div>
      </div>
    );
  }

  return <AuditLogTable scope="project" projectId={projectId} />;
}
