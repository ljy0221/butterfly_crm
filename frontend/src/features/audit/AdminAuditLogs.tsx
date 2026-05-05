import { PageHeader } from "@/components/common";
import AuditLogTable from "./AuditLogTable";

/**
 * Sprint 7 — `/admin/audit` 진입점.
 *
 * 라우터에서 RequireAdmin 가드로 감싸므로 여기서는 별도 권한 검사 불필요.
 * AuditLogTable scope="admin" — projectId 필터 노출 (없으면 전체).
 */
export default function AdminAuditLogs() {
  return (
    <div>
      <PageHeader
        title="감사 로그 (전체)"
        subtitle="모든 프로젝트의 감사 이벤트를 가로 조회합니다."
      />
      <AuditLogTable scope="admin" />
    </div>
  );
}
