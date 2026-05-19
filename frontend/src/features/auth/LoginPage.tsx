import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Icon, Input } from "@/components/common";
import { useLoginMutation } from "@/hooks/queries/auth";
import { mapAuthError } from "@/lib/auth-errors";
import { toast } from "@/lib/ui-bus";
import { useAuthStore, type AuthRole } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import { loginSchema } from "@/schemas/auth";
import { ApiError } from "@/api/types";

interface FieldErrors {
  email?: string;
  password?: string;
}

/**
 * 로그인/이미 인증된 사용자 진입 시 목적지 분기.
 * - ADMIN → 신청서 승인 페이지
 * - 단일 프로젝트 소속(OWNER/EDITOR/VIEWER + projectId 있음) → 대시보드 직행
 * - 그 외 → 프로젝트 선택
 */
function resolveDest(
  role: AuthRole | undefined,
  projectId: number | null | undefined,
  status?: "PENDING" | "ACTIVE",
): string {
  if (role === "ADMIN") return "/admin/applications";
  if (status === "PENDING") return "/projects";
  if (projectId != null) return `/p/${projectId}/dashboard`;
  return "/projects";
}

export default function LoginPage() {
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const setAuth = useAuthStore((s) => s.setAuth);
  const member = useAuthStore((s) => s.member);
  const clearProject = useProjectStore((s) => s.clear);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [bannerError, setBannerError] = useState<string | null>(null);

  const loginMutation = useLoginMutation();

  // 이미 로그인되어 있으면 자동으로 라우팅
  useEffect(() => {
    if (isAuthenticated && member) {
      navigate(resolveDest(member.role, member.projectId, member.status), { replace: true });
    }
  }, [isAuthenticated, member, navigate]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setBannerError(null);

    const parsed = loginSchema.safeParse({ email, password });
    if (!parsed.success) {
      const errors: FieldErrors = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0] as keyof FieldErrors;
        if (key && !errors[key]) errors[key] = issue.message;
      }
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});

    loginMutation.mutate(parsed.data, {
      onSuccess: (res) => {
        const role = res.member.roleType as AuthRole;
        const status = res.member.status as "PENDING" | "ACTIVE" | undefined;
        // 이전 세션의 프로젝트 컨텍스트 잔존 방지 — 로그인 시 항상 초기화
        clearProject();
        setAuth({
          accessToken: res.accessToken ?? "",
          refreshToken: res.refreshToken ?? "",
          member: {
            memberId: res.member.memberId,
            email: res.member.email,
            name: res.member.name,
            phone: res.member.phone,
            role,
            projectId: res.member.projectId,
            status,
          },
        });
        toast(`${res.member.name}님 환영합니다`, "success");
        navigate(resolveDest(role, res.member.projectId, status), { replace: true });
      },
      onError: (err) => {
        const mapped = mapAuthError(err);
        if (err instanceof ApiError && err.code === "AUTH_016") {
          // 비밀번호 미설정 — Owner 초대 후
          toast(mapped.message, "warning");
          return;
        }
        if (err instanceof ApiError && err.code === "AUTH_006") {
          setBannerError(mapped.message);
          return;
        }
        toast(mapped.message, "error");
      },
    });
  };

  return (
    <div style={{ width: 420, display: "flex", flexDirection: "column", alignItems: "center" }}>
      {/* 브랜드 헤더 */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 10,
          marginBottom: "var(--space-2)",
        }}
      >
        <div
          aria-hidden
          style={{
            width: 418,
            height: 128,
            borderRadius: 10,
            // background: "var(--color-primary-strong)",
            color: "#fff",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            fontFamily: "var(--font-display)",
            fontWeight: 700,
            fontSize: 18,
          }}
        >
          <img src="/assets/logo.png" alt="Butterfly Logo" style={{ 
            width: "100%", 
            height: "100%", 
            objectFit: "contain", 
            // padding: 0
          }}/>
        </div>
        {/* <span
          style={{
            fontFamily: "var(--font-display)",
            fontSize: 22,
            fontWeight: 700,
            color: "var(--fg-strong)",
          }}
        >
          Butterfly
        </span> */}
      </div>
      <div
        style={{
          fontSize: 12,
          color: "var(--fg-secondary)",
          marginBottom: "var(--space-6)",
          letterSpacing: "0.02em",
        }}
      >
        CRM Operator Console
      </div>

      <Card title="로그인" style={{ width: "100%" }}>
        <form onSubmit={handleSubmit} noValidate>
          <Input
            label="이메일"
            type="email"
            value={email}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setEmail(e.target.value)
            }
            placeholder="you@example.com"
            autoFocus
            autoComplete="email"
            error={!!fieldErrors.email}
            help={fieldErrors.email}
          />
          <div style={{ height: "var(--space-3)" }} />
          <div style={{ position: "relative" }}>
            <Input
              label="비밀번호"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                setPassword(e.target.value)
              }
              placeholder="••••••••"
              autoComplete="current-password"
              error={!!fieldErrors.password}
              help={fieldErrors.password}
            />
            <button
              type="button"
              aria-label={showPassword ? "비밀번호 숨기기" : "비밀번호 표시"}
              onClick={() => setShowPassword((v) => !v)}
              style={{
                position: "absolute",
                right: 10,
                top: 28,
                width: 28,
                height: 28,
                background: "transparent",
                border: 0,
                color: "var(--fg-faint)",
                cursor: "pointer",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              {showPassword ? Icon.eyeOff(14) : Icon.eye(14)}
            </button>
          </div>

          {bannerError && (
            <div
              role="alert"
              style={{
                marginTop: "var(--space-4)",
                padding: "10px 12px",
                background: "var(--state-error-bg)",
                color: "#b91c1c",
                borderRadius: 8,
                fontSize: 12,
                fontWeight: 500,
              }}
            >
              {bannerError}
            </div>
          )}

          <div style={{ height: "var(--space-5)" }} />
          <Button
            type="submit"
            variant="primaryStrong"
            size="lg"
            disabled={loginMutation.isPending}
            style={{
              width: "100%",
              justifyContent: "center",
              opacity: loginMutation.isPending ? 0.5 : 1,
              cursor: loginMutation.isPending ? "not-allowed" : "pointer",
            }}
          >
            {loginMutation.isPending ? "로그인 중..." : "로그인"}
          </Button>
        </form>
      </Card>

      {/* SaaS 진입 안내 — 신청서가 1차 진입점 */}
      <div
        style={{
          marginTop: "var(--space-5)",
          width: "100%",
          background: "var(--bg-surface)",
          border: "1px solid var(--border-default)",
          borderRadius: "var(--radius-lg)",
          padding: "16px 18px",
          fontSize: 12,
          color: "var(--fg-primary)",
          boxShadow: "var(--shadow-1)",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            marginBottom: 10,
          }}
        >
          <span
            style={{
              fontSize: 11,
              fontWeight: 700,
              padding: "2px 8px",
              borderRadius: 999,
              background: "var(--color-primary-soft)",
              color: "var(--color-primary-strong)",
              letterSpacing: "0.02em",
            }}
          >
            처음이신가요?
          </span>
          <span style={{ color: "var(--fg-secondary)", fontSize: 12 }}>
            Butterfly는 서비스 신청서부터 시작해요.
          </span>
        </div>
        <Link
          to="/apply"
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            padding: "10px 12px",
            background: "var(--color-primary-strong)",
            color: "#fff",
            borderRadius: "var(--radius-md)",
            textDecoration: "none",
            fontWeight: 500,
            fontSize: 13,
          }}
        >
          <span>서비스 신청하기</span>
          <span aria-hidden>→</span>
        </Link>
        <div
          style={{
            marginTop: 10,
            paddingTop: 10,
            borderTop: "1px dashed var(--border-default)",
            color: "var(--fg-secondary)",
            lineHeight: 1.6,
          }}
        >
          승인이 완료되면 이메일로 OWNER 비밀번호 설정 링크와 회사 인증번호가
          전달돼요.
          <br />
          <span style={{ color: "var(--fg-muted)" }}>
            이미 OWNER로부터 회사 인증번호를 받으셨나요?
          </span>{" "}
          <Link
            to="/signup"
            style={{ color: "var(--fg-link)", fontWeight: 500 }}
          >
            직원 회원가입
          </Link>
        </div>
      </div>

      {import.meta.env.DEV && (
        <div
          style={{
            marginTop: "var(--space-4)",
            textAlign: "center",
          }}
        >
          <Link
            to="/demo"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 6,
              padding: "6px 12px",
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: "0.04em",
              color: "#b45309",
              background: "rgba(245,158,11,0.10)",
              border: "1px dashed rgba(245,158,11,0.40)",
              borderRadius: 999,
              textDecoration: "none",
              fontFamily: "inherit",
            }}
          >
            <span>DEV ONLY</span>
            <span style={{ color: "#92400e" }}>·</span>
            <span>개발자 데모 진입 →</span>
          </Link>
        </div>
      )}
    </div>
  );
}
