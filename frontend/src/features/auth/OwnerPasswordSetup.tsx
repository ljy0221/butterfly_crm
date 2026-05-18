import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Icon, Input } from "@/components/common";
import { useOwnerSetPasswordMutation } from "@/hooks/queries/auth";
import { ownerSetupSchema } from "@/schemas/auth";
import { mapAuthError } from "@/lib/auth-errors";
import { ApiError } from "@/api/types";
import { toast } from "@/lib/ui-bus";

interface FieldErrors {
  password?: string;
  passwordConfirm?: string;
}

export default function OwnerPasswordSetup() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get("token");

  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [linkExpired, setLinkExpired] = useState(false);

  const mutation = useOwnerSetPasswordMutation();

  useEffect(() => {
    if (!token) {
      toast("유효하지 않은 링크입니다", "error");
      navigate("/login", { replace: true });
    }
  }, [token, navigate]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrors({});

    const r = ownerSetupSchema.safeParse({ password, passwordConfirm });
    if (!r.success) {
      const errs: FieldErrors = {};
      for (const issue of r.error.errors) {
        const key = issue.path[0] as keyof FieldErrors;
        if (key) errs[key] = issue.message;
      }
      setErrors(errs);
      return;
    }
    if (!token) return;

    try {
      await mutation.mutateAsync({ token, password });
      toast("비밀번호가 설정되었습니다. 로그인해주세요.", "success");
      navigate("/login", { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        const mapped = mapAuthError(err);
        if (err.code === "AUTH_017" || err.code === "AUTH_008") {
          setLinkExpired(true);
        } else {
          toast(mapped.message, "error");
        }
      } else {
        toast("비밀번호 설정 중 오류가 발생했습니다", "error");
      }
    }
  };

  return (
    <Card style={{ width: 420, padding: "var(--space-6)" }} title={null}>
      <h1 style={{ fontSize: 22, margin: "0 0 6px", fontWeight: 700 }}>
        비밀번호 설정
      </h1>
      <p
        style={{
          color: "var(--fg-secondary)",
          fontSize: 12,
          margin: "0 0 18px",
        }}
      >
        Owner 계정의 비밀번호를 처음 설정합니다.
      </p>

      {linkExpired ? (
        <div
          style={{
            padding: "16px",
            background: "var(--state-warning-bg)",
            borderRadius: "var(--radius-md)",
            color: "var(--color-warning)",
            fontSize: 13,
            textAlign: "center",
          }}
        >
          이 링크는 만료되었거나 유효하지 않습니다.
          <br />
          관리자에게 문의해주세요.
        </div>
      ) : (
        <form onSubmit={handleSubmit}>
          <div style={{ position: "relative" }}>
            <Input
              label="새 비밀번호"
              type={showPassword ? "text" : "password"}
              value={password}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                setPassword(e.target.value);
                if (Object.keys(errors).length > 0) setErrors({});
              }}
              autoComplete="new-password"
              autoFocus
              error={errors.password}
              help="8자 이상, 영소문자 + 숫자 포함"
              style={{}}
            />
            <button
              type="button"
              aria-label={showPassword ? "비밀번호 숨김" : "비밀번호 표시"}
              onClick={() => setShowPassword((v) => !v)}
              style={passwordToggleBtn()}
            >
              {Icon.eye(14)}
            </button>
          </div>

          <div style={{ height: "var(--space-3)" }} />

          <div style={{ position: "relative" }}>
            <Input
              label="비밀번호 확인"
              type={showPasswordConfirm ? "text" : "password"}
              value={passwordConfirm}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                setPasswordConfirm(e.target.value);
                if (Object.keys(errors).length > 0) setErrors({});
              }}
              autoComplete="new-password"
              error={errors.passwordConfirm}
              style={{}}
            />
            <button
              type="button"
              aria-label={
                showPasswordConfirm ? "비밀번호 숨김" : "비밀번호 표시"
              }
              onClick={() => setShowPasswordConfirm((v) => !v)}
              style={passwordToggleBtn()}
            >
              {Icon.eye(14)}
            </button>
          </div>

          <div style={{ height: "var(--space-5)" }} />

          <Button
            type="submit"
            variant="primaryStrong"
            size="lg"
            disabled={mutation.isPending}
            style={{
              width: "100%",
              justifyContent: "center",
              opacity: mutation.isPending ? 0.5 : 1,
              cursor: mutation.isPending ? "not-allowed" : "pointer",
            }}
          >
            {mutation.isPending ? "설정 중..." : "비밀번호 설정"}
          </Button>
        </form>
      )}

      <div
        style={{
          marginTop: 16,
          fontSize: 12,
          color: "var(--fg-secondary)",
          textAlign: "center",
        }}
      >
        <Link
          to="/login"
          style={{ color: "var(--fg-link)", textDecoration: "none" }}
        >
          로그인으로 돌아가기
        </Link>
      </div>
    </Card>
  );
}

function passwordToggleBtn(): React.CSSProperties {
  return {
    position: "absolute",
    right: 10,
    top: 32,
    background: "none",
    border: "none",
    cursor: "pointer",
    color: "var(--fg-secondary)",
    padding: 4,
  };
}
