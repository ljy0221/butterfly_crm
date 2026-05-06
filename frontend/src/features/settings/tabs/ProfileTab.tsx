import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, Icon, Input, Tag } from "@/components/common";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import {
  useChangePasswordMutation,
  useChangePhoneMutation,
  useMyInfoQuery,
  useWithdrawMutation,
} from "@/hooks/queries/auth";
import { mapAuthError } from "@/lib/auth-errors";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { passwordSchema, phoneSchema } from "@/schemas/auth";

/**
 * 프로필 탭 — 회원 정보 조회 + 비밀번호 변경 + 전화번호 변경 + 회원 탈퇴.
 *
 * 백엔드 AuthController의 다음 5개 엔드포인트를 모두 노출한다:
 *  - GET    /api/v1/auth/me              (useMyInfoQuery)
 *  - PUT    /api/v1/auth/password         (useChangePasswordMutation)
 *  - PUT    /api/v1/auth/phone            (useChangePhoneMutation)
 *  - DELETE /api/v1/auth/withdraw         (useWithdrawMutation)
 *  - POST   /api/v1/auth/logout           (UserMenuModal에서 호출)
 *
 * 권한: 누구든 자기 계정에 대한 작업 (member 인증만 통과하면 됨).
 *       OWNER는 PROJ_001로 탈퇴 차단됨 → 에러 매핑 후 토스트.
 */
export default function ProfileTab() {
  const navigate = useNavigate();
  const member = useAuthStore((s) => s.member);
  const clearAuth = useAuthStore((s) => s.clear);
  const clearProject = useProjectStore((s) => s.clear);

  const meQ = useMyInfoQuery(!!member);

  if (!member) {
    return null;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      {/* 회원 정보 조회 */}
      <Card title="내 정보" subtitle="현재 로그인한 계정 정보">
        {meQ.isLoading ? (
          <SkeletonRows rows={4} />
        ) : meQ.isError ? (
          <ErrorBox onRetry={() => meQ.refetch()} />
        ) : meQ.data ? (
          <InfoRows data={meQ.data} />
        ) : null}
      </Card>

      {/* 전화번호 변경 */}
      <ChangePhoneSection currentPhone={meQ.data?.phone ?? member.phone ?? ""} />

      {/* 비밀번호 변경 */}
      <ChangePasswordSection />

      {/* 회원 탈퇴 (위험 영역) */}
      <WithdrawSection
        onWithdrawn={() => {
          clearAuth();
          clearProject();
          navigate("/login", { replace: true });
        }}
      />
    </div>
  );
}

// ---------- 회원 정보 출력 ----------

function InfoRows({
  data,
}: {
  data: {
    memberId: number;
    email: string;
    name: string;
    phone: string;
    roleType: "ADMIN" | "OWNER" | "EDITOR" | "VIEWER";
    projectId: number | null;
    createdAt: string;
  };
}) {
  const rows: Array<[string, React.ReactNode]> = [
    ["이름", data.name],
    ["이메일", data.email],
    ["전화번호", data.phone],
    ["권한", <Tag tone={roleTone(data.roleType)}>{data.roleType}</Tag>],
    [
      "가입일",
      data.createdAt ? data.createdAt.replace("T", " ").slice(0, 19) : "—",
    ],
  ];
  return (
    <dl style={{ margin: 0, display: "grid", gridTemplateColumns: "120px 1fr", rowGap: 10 }}>
      {rows.map(([k, v]) => (
        <FragmentRow key={k} k={k} v={v} />
      ))}
    </dl>
  );
}

function FragmentRow({ k, v }: { k: string; v: React.ReactNode }) {
  return (
    <>
      <dt style={{ fontSize: 12, color: "var(--fg-secondary)", margin: 0 }}>{k}</dt>
      <dd style={{ fontSize: 13, color: "var(--fg-strong)", margin: 0 }}>{v}</dd>
    </>
  );
}

function roleTone(role: string): "info" | "success" | "warning" | "default" {
  if (role === "ADMIN") return "warning";
  if (role === "OWNER") return "info";
  if (role === "EDITOR") return "success";
  return "default";
}

// ---------- 전화번호 변경 ----------

function ChangePhoneSection({ currentPhone }: { currentPhone: string }) {
  const [phone, setPhone] = useState(currentPhone);
  const [error, setError] = useState<string | undefined>();
  const mut = useChangePhoneMutation();

  useEffect(() => {
    setPhone(currentPhone);
  }, [currentPhone]);

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = phoneSchema.safeParse(phone);
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "올바른 전화번호를 입력하세요");
      return;
    }
    setError(undefined);
    mut.mutate(
      { phone: parsed.data },
      {
        onSuccess: () => toast("전화번호가 변경되었습니다", "success"),
        onError: (err) => {
          const m = mapAuthError(err);
          if (m.field === "phone") setError(m.message);
          else toast(m.message, "error");
        },
      },
    );
  };

  const dirty = phone !== currentPhone;

  return (
    <Card title="전화번호 변경" subtitle="알림 발송에 사용됩니다">
      <form onSubmit={onSubmit}>
        <Input
          label="전화번호 (-제외 11자리)"
          value={phone}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setPhone(e.target.value.replace(/[^0-9]/g, ""))
          }
          placeholder="01012345678"
          maxLength={11}
          autoComplete="tel"
          error={!!error}
          help={error}
        />
        <div style={{ marginTop: 12, display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <Button
            type="submit"
            variant="primaryStrong"
            size="md"
            disabled={!dirty || mut.isPending}
            style={{ opacity: !dirty || mut.isPending ? 0.5 : 1 }}
          >
            {mut.isPending ? "변경 중..." : "변경"}
          </Button>
        </div>
      </form>
    </Card>
  );
}

// ---------- 비밀번호 변경 ----------

function ChangePasswordSection() {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const mut = useChangePasswordMutation();

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!currentPassword) next.currentPassword = "현재 비밀번호를 입력하세요";
    const pw = passwordSchema.safeParse(newPassword);
    if (!pw.success) next.newPassword = pw.error.issues[0]?.message ?? "올바른 비밀번호";
    if (newPassword !== confirm)
      next.confirm = "비밀번호 확인이 일치하지 않습니다";
    if (Object.keys(next).length > 0) {
      setErrors(next);
      return;
    }
    setErrors({});
    mut.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => {
          toast("비밀번호가 변경되었습니다", "success");
          setCurrentPassword("");
          setNewPassword("");
          setConfirm("");
        },
        onError: (err) => {
          const m = mapAuthError(err);
          if (m.field === "currentPassword")
            setErrors({ currentPassword: m.message });
          else if (m.field === "newPassword")
            setErrors({ newPassword: m.message });
          else toast(m.message, "error");
        },
      },
    );
  };

  return (
    <Card title="비밀번호 변경" subtitle="8~20자, 영소문자 + 숫자 포함">
      <form onSubmit={onSubmit}>
        <Input
          label="현재 비밀번호"
          type="password"
          value={currentPassword}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setCurrentPassword(e.target.value)
          }
          autoComplete="current-password"
          error={!!errors.currentPassword}
          help={errors.currentPassword}
        />
        <div style={{ height: 12 }} />
        <Input
          label="새 비밀번호"
          type="password"
          value={newPassword}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setNewPassword(e.target.value)
          }
          autoComplete="new-password"
          error={!!errors.newPassword}
          help={errors.newPassword}
        />
        <div style={{ height: 12 }} />
        <Input
          label="새 비밀번호 확인"
          type="password"
          value={confirm}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setConfirm(e.target.value)
          }
          autoComplete="new-password"
          error={!!errors.confirm}
          help={errors.confirm}
        />
        <div style={{ marginTop: 16, display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <Button
            type="submit"
            variant="primaryStrong"
            size="md"
            disabled={mut.isPending}
            style={{ opacity: mut.isPending ? 0.5 : 1 }}
          >
            {mut.isPending ? "변경 중..." : "변경"}
          </Button>
        </div>
      </form>
    </Card>
  );
}

// ---------- 회원 탈퇴 (위험) ----------

function WithdrawSection({ onWithdrawn }: { onWithdrawn: () => void }) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | undefined>();
  const mut = useWithdrawMutation();
  const member = useAuthStore((s) => s.member);

  const isOwner = member?.role === "OWNER";

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!password) {
      setError("비밀번호를 입력하세요");
      return;
    }
    const ok = await confirmDialog({
      title: "정말 탈퇴하시겠습니까?",
      message:
        "계정과 모든 활동 기록은 소프트 삭제됩니다. 이 작업은 되돌릴 수 없습니다.",
      confirmText: "탈퇴",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    setError(undefined);
    mut.mutate(
      { password },
      {
        onSuccess: () => {
          toast("탈퇴가 완료되었습니다", "info");
          onWithdrawn();
        },
        onError: (err) => {
          const m = mapAuthError(err);
          if (m.field === "password") setError(m.message);
          else toast(m.message, "error");
        },
      },
    );
  };

  return (
    <Card
      title="회원 탈퇴"
      subtitle="이 작업은 되돌릴 수 없습니다"
      style={{ borderColor: "rgba(239,68,68,0.30)" }}
    >
      {isOwner && (
        <div
          role="alert"
          style={{
            marginBottom: 12,
            padding: "10px 12px",
            background: "var(--state-warning-bg)",
            color: "#b45309",
            borderRadius: 8,
            fontSize: 12,
            lineHeight: 1.6,
          }}
        >
          <strong>OWNER는 탈퇴할 수 없습니다.</strong>
          <br />
          탈퇴하려면 먼저 다른 멤버에게 OWNER 권한을 이양해야 합니다 (PROJ_001).
        </div>
      )}
      <form onSubmit={onSubmit}>
        <Input
          label="비밀번호 확인"
          type="password"
          value={password}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            setPassword(e.target.value)
          }
          autoComplete="current-password"
          placeholder="••••••••"
          error={!!error}
          help={error}
          disabled={isOwner}
        />
        <div style={{ marginTop: 12, display: "flex", justifyContent: "flex-end" }}>
          <Button
            type="submit"
            variant="primary"
            size="md"
            disabled={isOwner || mut.isPending}
            style={{
              background: "#dc2626",
              opacity: isOwner || mut.isPending ? 0.5 : 1,
              cursor: isOwner ? "not-allowed" : "pointer",
            }}
          >
            <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
              {Icon.trash(13)}
              {mut.isPending ? "탈퇴 중..." : "회원 탈퇴"}
            </span>
          </Button>
        </div>
      </form>
    </Card>
  );
}

// ---------- 공통 ----------


function SkeletonRows({ rows }: { rows: number }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          style={{
            height: 16,
            background:
              "linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 37%, #f1f5f9 63%)",
            backgroundSize: "400% 100%",
            borderRadius: 4,
            animation: "skeleton 1.4s ease infinite",
          }}
        />
      ))}
      <style>{`@keyframes skeleton { 0%{background-position:100% 50%} 100%{background-position:0 50%} }`}</style>
    </div>
  );
}

function ErrorBox({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      style={{
        padding: 16,
        textAlign: "center",
        color: "var(--fg-secondary)",
        fontSize: 13,
      }}
    >
      회원 정보를 불러오지 못했습니다.{" "}
      <button
        type="button"
        onClick={onRetry}
        style={{
          background: "transparent",
          border: 0,
          color: "var(--fg-link)",
          cursor: "pointer",
          fontFamily: "inherit",
          fontSize: 13,
        }}
      >
        다시 시도
      </button>
    </div>
  );
}
