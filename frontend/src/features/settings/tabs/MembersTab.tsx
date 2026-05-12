import { useState } from "react";
import { Card, Tag, Avatar, Icon } from "@/components/common";
import {
  useApproveMemberMutation,
  useDeleteMemberMutation,
  useMembersQuery,
  usePendingMembersQuery,
  useRejectMemberMutation,
  useUpdateMemberRoleMutation,
} from "@/hooks/queries/tenant";
import { useAuthStore } from "@/stores/auth";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { ApiError } from "@/api/types";
import {
  TENANT_REFRESH_CODES,
  mapTenantError,
} from "@/lib/tenant-errors";
import type { ProjectMemberResponse } from "@/api/tenant";
import { useQueryClient } from "@tanstack/react-query";
import { tenantKeys } from "@/hooks/queries/tenant";
import type { RoleType } from "@/schemas/tenant";

const ROLE_TONE: Record<RoleType, "warning" | "info" | "default" | "soft"> = {
  OWNER: "warning",
  EDITOR: "info",
  VIEWER: "default",
  ADMIN: "soft",
};

const ROLE_LABEL: Record<RoleType, string> = {
  OWNER: "OWNER",
  EDITOR: "EDITOR",
  VIEWER: "VIEWER",
  ADMIN: "ADMIN",
};

/**
 * Sprint 2 — 멤버 관리 탭.
 *
 * 권한:
 *  - VIEWER/EDITOR: 권한 셀은 Tag만, 액션 컬럼 숨김.
 *  - OWNER/ADMIN: 권한 Select + 휴지통.
 *  - OWNER 행은 disabled + lock(권한 변경/삭제 모두 PROJ_007/008로 차단됨)
 *  - 본인 행 액션 disabled.
 */
export default function MembersTab({ projectId }: { projectId: number }) {
  const me = useAuthStore((s) => s.member);
  const meRole = me?.role;
  const isManager = meRole === "OWNER" || meRole === "ADMIN";

  const membersQ = useMembersQuery(projectId);
  const pendingQ = usePendingMembersQuery(projectId, isManager);
  const updateRole = useUpdateMemberRoleMutation(projectId);
  const deleteMember = useDeleteMemberMutation(projectId);
  const approveMember = useApproveMemberMutation(projectId);
  const rejectMember = useRejectMemberMutation(projectId);
  const qc = useQueryClient();

  const [pendingId, setPendingId] = useState<number | null>(null);

  const handleRoleChange = async (
    member: ProjectMemberResponse,
    next: RoleType,
  ) => {
    if (next === member.roleType) return;
    setPendingId(member.memberId);
    try {
      await updateRole.mutateAsync({
        memberId: member.memberId,
        req: { roleType: next },
      });
      toast(`${member.name} 님의 권한을 ${next}로 변경했습니다`, "success");
    } catch (err) {
      const { message } = mapTenantError(err);
      toast(message, "error");
      if (
        err instanceof ApiError &&
        err.code &&
        TENANT_REFRESH_CODES.has(err.code)
      ) {
        qc.invalidateQueries({ queryKey: tenantKeys.members(projectId) });
      }
    } finally {
      setPendingId(null);
    }
  };

  const handleDelete = async (member: ProjectMemberResponse) => {
    const ok = await confirmDialog({
      title: "멤버 삭제",
      message: `${member.name} 님을 프로젝트에서 제외합니다. 즉시 로그아웃됩니다.`,
      confirmText: "삭제",
      tone: "danger",
    });
    if (!ok) return;
    setPendingId(member.memberId);
    try {
      await deleteMember.mutateAsync(member.memberId);
      toast(`${member.name} 님을 삭제했습니다`, "success");
    } catch (err) {
      const { message } = mapTenantError(err);
      toast(message, "error");
      if (
        err instanceof ApiError &&
        err.code &&
        TENANT_REFRESH_CODES.has(err.code)
      ) {
        qc.invalidateQueries({ queryKey: tenantKeys.members(projectId) });
      }
    } finally {
      setPendingId(null);
    }
  };

  const members = membersQ.data ?? [];
  const pendingMembers = pendingQ.data ?? [];
  const colCount = isManager ? 4 : 3;

  const handleApprove = async (m: ProjectMemberResponse) => {
    try {
      await approveMember.mutateAsync(m.memberId);
      toast(`${m.name} 님의 가입을 승인했습니다`, "success");
    } catch {
      toast("승인 처리 중 오류가 발생했습니다", "error");
    }
  };

  const handleReject = async (m: ProjectMemberResponse) => {
    const ok = await confirmDialog({
      title: "가입 거절",
      message: `${m.name} 님의 가입 요청을 거절합니다.`,
      confirmText: "거절",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await rejectMember.mutateAsync(m.memberId);
      toast(`${m.name} 님의 가입을 거절했습니다`, "success");
    } catch {
      toast("거절 처리 중 오류가 발생했습니다", "error");
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
    {isManager && pendingMembers.length > 0 && (
      <Card title={`승인 대기 (${pendingMembers.length}명)`} padding={0}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead>
            <tr style={{ background: "#fffbeb" }}>
              <th style={th()}>이름</th>
              <th style={th()}>이메일</th>
              <th style={{ ...th(), width: 140 }}>액션</th>
            </tr>
          </thead>
          <tbody>
            {pendingMembers.map((m) => (
              <tr key={m.memberId} style={{ borderBottom: "1px solid #f1f5f9" }}>
                <td style={td()}>
                  <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                    <Avatar name={m.name} size={28} />
                    <span style={{ fontWeight: 500 }}>{m.name}</span>
                  </div>
                </td>
                <td style={{ ...td(), color: "#64748b", fontSize: 12 }}>{m.email}</td>
                <td style={td()}>
                  <div style={{ display: "flex", gap: 6 }}>
                    <button
                      onClick={() => handleApprove(m)}
                      style={{
                        padding: "4px 10px", fontSize: 12, borderRadius: 6, border: "1px solid #16a34a",
                        background: "#f0fdf4", color: "#16a34a", cursor: "pointer", fontFamily: "inherit",
                      }}
                    >
                      승인
                    </button>
                    <button
                      onClick={() => handleReject(m)}
                      style={{
                        padding: "4px 10px", fontSize: 12, borderRadius: 6, border: "1px solid #fecaca",
                        background: "#fff", color: "#b91c1c", cursor: "pointer", fontFamily: "inherit",
                      }}
                    >
                      거절
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    )}
    <Card
      title={`멤버${members.length > 0 ? ` (${members.length}명)` : ""}`}
      padding={0}
    >
      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
        <thead>
          <tr style={{ background: "#f8fafc" }}>
            <th style={th()}>이름</th>
            <th style={th()}>이메일</th>
            <th style={th()}>권한</th>
            {isManager && <th style={{ ...th(), width: 80 }}>액션</th>}
          </tr>
        </thead>
        <tbody>
          {membersQ.isLoading &&
            Array.from({ length: 5 }).map((_, i) => (
              <tr key={i}>
                <td colSpan={colCount} style={{ padding: 0 }}>
                  <div
                    style={{
                      height: 48,
                      margin: "0 16px",
                      borderBottom: "1px solid #f1f5f9",
                      background:
                        "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
                      backgroundSize: "200% 100%",
                      animation: "shimmer 1.4s infinite",
                    }}
                  />
                </td>
              </tr>
            ))}
          {membersQ.isError && (
            <tr>
              <td
                colSpan={colCount}
                style={{
                  textAlign: "center",
                  padding: 32,
                  color: "#b91c1c",
                  fontSize: 13,
                }}
              >
                {mapTenantError(membersQ.error).message}
              </td>
            </tr>
          )}
          {!membersQ.isLoading && !membersQ.isError && members.length === 0 && (
            <tr>
              <td
                colSpan={colCount}
                style={{
                  textAlign: "center",
                  padding: 48,
                  color: "#94a3b8",
                  fontSize: 13,
                }}
              >
                멤버가 없습니다
              </td>
            </tr>
          )}
          {!membersQ.isLoading &&
            !membersQ.isError &&
            members.map((m) => {
              const isMe = me?.memberId === m.memberId;
              const isOwnerRow = m.roleType === "OWNER";
              const isPending = pendingId === m.memberId;
              const lockChange = !isManager || isOwnerRow || isMe || isPending;
              const lockDelete = !isManager || isOwnerRow || isMe || isPending;
              return (
                <tr
                  key={m.memberId}
                  style={{
                    borderBottom: "1px solid #f1f5f9",
                    opacity: isPending ? 0.5 : 1,
                    transition: "opacity 120ms",
                  }}
                >
                  <td style={td()}>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <Avatar name={m.name} size={28} />
                      <span style={{ fontWeight: 500, color: "#1f2937" }}>
                        {m.name}
                      </span>
                      {isMe && (
                        <Tag tone="info" style={{ marginLeft: 4 }}>
                          나
                        </Tag>
                      )}
                    </div>
                  </td>
                  <td style={{ ...td(), color: "#64748b", fontSize: 12 }}>
                    {m.email}
                  </td>
                  <td style={td()}>
                    {isManager && !isOwnerRow && !isMe ? (
                      <RoleSelect
                        value={m.roleType}
                        disabled={lockChange}
                        onChange={(next) => handleRoleChange(m, next)}
                      />
                    ) : (
                      <span
                        style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
                      >
                        <Tag tone={ROLE_TONE[m.roleType]}>{ROLE_LABEL[m.roleType]}</Tag>
                        {(isOwnerRow || isMe) && (
                          <span
                            title={
                              isMe && isOwnerRow
                                ? "본인(OWNER) 권한은 변경할 수 없습니다"
                                : isOwnerRow
                                  ? "OWNER 권한은 직접 변경할 수 없습니다"
                                  : "본인 권한은 변경할 수 없습니다"
                            }
                            aria-label={isOwnerRow ? "OWNER 잠금" : "본인 행 잠금"}
                            style={{
                              color: "#b45309",
                              display: "inline-flex",
                              alignItems: "center",
                            }}
                          >
                            {Icon.lock(12)}
                          </span>
                        )}
                      </span>
                    )}
                  </td>
                  {isManager && (
                    <td style={td()}>
                      {lockDelete ? (
                        <span style={{ color: "#cbd5e1" }}>—</span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => handleDelete(m)}
                          aria-label={`멤버 삭제: ${m.name}`}
                          title={`${m.name} 삭제`}
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            width: 28,
                            height: 28,
                            border: "1px solid #fecaca",
                            borderRadius: 6,
                            background: "#fff",
                            color: "#b91c1c",
                            cursor: "pointer",
                          }}
                        >
                          {Icon.trash(14)}
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
        </tbody>
      </table>
      <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>
    </Card>
    </div>
  );
}

function RoleSelect({
  value,
  disabled,
  onChange,
}: {
  value: RoleType;
  disabled: boolean;
  onChange: (v: RoleType) => void;
}) {
  return (
    <select
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value as RoleType)}
      aria-label="권한 변경"
      style={{
        height: 30,
        padding: "0 28px 0 10px",
        fontFamily: "inherit",
        fontSize: 12,
        color: "#1f2937",
        background: disabled ? "#f8fafc" : "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 6,
        outline: "none",
        appearance: "none",
        cursor: disabled ? "not-allowed" : "pointer",
        backgroundImage:
          "url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2394a3b8' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M6 9l6 6 6-6'/></svg>\")",
        backgroundRepeat: "no-repeat",
        backgroundPosition: "right 8px center",
      }}
    >
      <option value="EDITOR">EDITOR</option>
      <option value="VIEWER">VIEWER</option>
    </select>
  );
}

function th(): React.CSSProperties {
  return {
    textAlign: "left",
    padding: "10px 16px",
    fontSize: 12,
    color: "#64748b",
    fontWeight: 500,
    borderBottom: "1px solid #e2e8f0",
  };
}

function td(): React.CSSProperties {
  return {
    padding: "12px 16px",
    fontSize: 13,
    color: "#1f2937",
    verticalAlign: "middle",
  };
}
