import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Icon } from "@/components/common";
import EmailVerifyStep, {
  type EmailVerifyStepValue,
} from "./steps/EmailVerifyStep";
import CompanyAuthStep from "./steps/CompanyAuthStep";
import PersonalInfoStep, {
  type PersonalInfoErrors,
  type PersonalInfoValue,
} from "./steps/PersonalInfoStep";
import PasswordStep, {
  type PasswordStepErrors,
  type PasswordStepValue,
} from "./steps/PasswordStep";
import { useSignupMutation } from "@/hooks/queries/auth";
import {
  companyAuthNoSchema,
  passwordStepSchema,
  personalInfoSchema,
} from "@/schemas/auth";
import { mapAuthError } from "@/lib/auth-errors";
import { ApiError } from "@/api/types";
import { toast } from "@/lib/ui-bus";

type Step = 1 | 2 | 3 | 4;

const STEP_LABELS = ["이메일 인증", "회사 인증번호", "개인정보", "비밀번호"];

export default function SignupPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>(1);

  const [emailValue, setEmailValue] = useState<EmailVerifyStepValue>({
    email: "",
    verified: false,
  });
  const [companyAuthNo, setCompanyAuthNo] = useState("");
  const [companyAuthError, setCompanyAuthError] = useState<string | null>(null);
  const [personalValue, setPersonalValue] = useState<PersonalInfoValue>({
    name: "",
    phone: "",
    birthDate: "",
  });
  const [personalErrors, setPersonalErrors] = useState<PersonalInfoErrors>({});
  const [passwordValue, setPasswordValue] = useState<PasswordStepValue>({
    password: "",
    passwordConfirm: "",
    agreedToTerms: false,
  });
  const [passwordErrors, setPasswordErrors] = useState<PasswordStepErrors>({});

  const signupMutation = useSignupMutation();

  const goNext = () => {
    if (step === 1) {
      if (!emailValue.verified) {
        toast("이메일 인증을 완료해주세요", "warning");
        return;
      }
      setStep(2);
      return;
    }
    if (step === 2) {
      const r = companyAuthNoSchema.safeParse(companyAuthNo);
      if (!r.success) {
        setCompanyAuthError(r.error.errors[0]?.message ?? "회사 인증번호를 입력하세요");
        return;
      }
      setCompanyAuthError(null);
      setStep(3);
      return;
    }
    if (step === 3) {
      const r = personalInfoSchema.safeParse(personalValue);
      if (!r.success) {
        const errs: PersonalInfoErrors = {};
        for (const issue of r.error.errors) {
          const key = issue.path[0] as keyof PersonalInfoErrors;
          if (key) errs[key] = issue.message;
        }
        setPersonalErrors(errs);
        return;
      }
      setPersonalErrors({});
      setStep(4);
      return;
    }
  };

  const goPrev = () => {
    if (step === 1) {
      navigate("/login");
      return;
    }
    setStep((s) => (s - 1) as Step);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (step !== 4) return;

    const r = passwordStepSchema.safeParse(passwordValue);
    if (!r.success) {
      const errs: PasswordStepErrors = {};
      for (const issue of r.error.errors) {
        const key = issue.path[0] as keyof PasswordStepErrors;
        if (key) errs[key] = issue.message;
      }
      setPasswordErrors(errs);
      return;
    }
    setPasswordErrors({});

    try {
      await signupMutation.mutateAsync({
        email: emailValue.email,
        password: passwordValue.password,
        name: personalValue.name,
        phone: personalValue.phone,
        birthDate: personalValue.birthDate,
        companyAuthNo,
        agreedToTerms: passwordValue.agreedToTerms,
      });
      toast("가입이 완료되었습니다. 로그인해주세요.", "success");
      navigate("/login", { state: { email: emailValue.email } });
    } catch (err) {
      if (err instanceof ApiError) {
        const mapped = mapAuthError(err);
        if (mapped.jumpStep) {
          setStep(mapped.jumpStep as Step);
          if (mapped.jumpStep === 1) {
            // 이메일 인증 다시 (서버에서 unverified 응답 시)
            setEmailValue((v) => ({ ...v, verified: false }));
          }
          if (mapped.jumpStep === 2) {
            setCompanyAuthError(mapped.message);
          }
          toast(mapped.message, "error");
        } else {
          toast(mapped.message, "error");
        }
      } else {
        toast("가입 처리 중 오류가 발생했습니다", "error");
      }
    }
  };

  return (
    <Card style={{ width: 440, padding: "var(--space-6)" }} title={null}>
      <button
        type="button"
        onClick={goPrev}
        aria-label="뒤로"
        style={{
          background: "none",
          border: "none",
          color: "var(--fg-secondary)",
          fontSize: 12,
          padding: 0,
          marginBottom: 12,
          cursor: "pointer",
          display: "flex",
          alignItems: "center",
          gap: 4,
        }}
      >
        {Icon.chevronLeft?.(12) ?? "←"} 뒤로
      </button>
      <h1 style={{ fontSize: 22, margin: "0 0 6px", fontWeight: 700 }}>
        직원 회원가입
      </h1>
      <p
        style={{
          color: "var(--fg-secondary)",
          fontSize: 12,
          margin: "0 0 12px",
          lineHeight: 1.6,
        }}
      >
        OWNER로부터 받은 <strong style={{ color: "var(--fg-strong)" }}>회사 인증번호</strong>로
        가입할 수 있습니다. 인증번호가 없다면 회사 관리자(OWNER)에게 요청하거나,
        회사 차원의 신규 도입이라면{" "}
        <Link to="/apply" style={{ color: "var(--fg-link)", fontWeight: 500 }}>
          서비스 신청
        </Link>
        부터 진행해 주세요.
      </p>

      {/* Progress dots */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 6,
          margin: "0 0 18px",
        }}
        aria-label={`회원가입 진행 ${step}/4`}
      >
        {[1, 2, 3, 4].map((n) => (
          <div key={n} style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <div
              style={{
                width: 8,
                height: 8,
                borderRadius: "50%",
                background:
                  n <= step ? "var(--color-primary-strong)" : "#cbd5e1",
              }}
            />
            {n < 4 && (
              <div
                style={{
                  width: 24,
                  height: 1,
                  background:
                    n < step ? "var(--color-primary-strong)" : "#e2e8f0",
                }}
              />
            )}
          </div>
        ))}
        <span
          style={{
            color: "var(--fg-secondary)",
            fontSize: 11,
            marginLeft: 8,
          }}
        >
          {step}/4 · {STEP_LABELS[step - 1]}
        </span>
      </div>

      <form onSubmit={handleSubmit}>
        {step === 1 && (
          <EmailVerifyStep value={emailValue} onChange={setEmailValue} />
        )}
        {step === 2 && (
          <CompanyAuthStep
            value={companyAuthNo}
            onChange={(v) => {
              setCompanyAuthNo(v);
              if (companyAuthError) setCompanyAuthError(null);
            }}
            error={companyAuthError}
          />
        )}
        {step === 3 && (
          <PersonalInfoStep
            value={personalValue}
            onChange={setPersonalValue}
            errors={personalErrors}
          />
        )}
        {step === 4 && (
          <PasswordStep
            value={passwordValue}
            onChange={setPasswordValue}
            errors={passwordErrors}
          />
        )}

        <div
          style={{
            display: "flex",
            gap: 8,
            marginTop: 20,
            justifyContent: "space-between",
          }}
        >
          <Button
            type="button"
            variant="ghost"
            size="md"
            onClick={goPrev}
            style={{}}
          >
            이전
          </Button>
          {step < 4 ? (
            <Button
              type="button"
              variant="primaryStrong"
              size="md"
              onClick={goNext}
              style={{}}
            >
              다음
            </Button>
          ) : (
            <Button
              type="submit"
              variant="primaryStrong"
              size="md"
              disabled={signupMutation.isPending}
              style={{
                opacity: signupMutation.isPending ? 0.5 : 1,
                cursor: signupMutation.isPending ? "not-allowed" : "pointer",
              }}
            >
              {signupMutation.isPending ? "가입 중..." : "가입 완료"}
            </Button>
          )}
        </div>
      </form>

      <div
        style={{
          marginTop: 16,
          fontSize: 12,
          color: "var(--fg-secondary)",
          textAlign: "center",
        }}
      >
        이미 계정이 있나요?{" "}
        <Link
          to="/login"
          style={{ color: "var(--fg-link)", textDecoration: "none" }}
        >
          로그인
        </Link>
      </div>
    </Card>
  );
}
