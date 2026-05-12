import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Checkbox, Input, BirthDatePicker } from "@/components/common";
import { useCreateApplicationMutation } from "@/hooks/queries/application";
import { applicationSchema } from "@/schemas/application";
import { ApiError } from "@/api/types";
import { toast } from "@/lib/ui-bus";

interface FormState {
  applicantName: string;
  email: string;
  phone: string;
  birthDate: string;
  businessName: string;
  businessNo: string;
  agreed: boolean;
}

interface FieldErrors {
  applicantName?: string;
  email?: string;
  phone?: string;
  birthDate?: string;
  businessName?: string;
  businessNo?: string;
  agreed?: string;
}

const INITIAL: FormState = {
  applicantName: "",
  email: "",
  phone: "",
  birthDate: "",
  businessName: "",
  businessNo: "",
  agreed: false,
};

export default function ApplyPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(INITIAL);
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submittedId, setSubmittedId] = useState<number | null>(null);

  const mutation = useCreateApplicationMutation();

  const update = <K extends keyof FormState>(key: K, val: FormState[K]) => {
    setForm((f) => ({ ...f, [key]: val }));
    if (errors[key as keyof FieldErrors]) {
      setErrors((e) => ({ ...e, [key]: undefined }));
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrors({});
    const r = applicationSchema.safeParse(form);
    if (!r.success) {
      const errs: FieldErrors = {};
      for (const issue of r.error.errors) {
        const key = issue.path[0] as keyof FieldErrors;
        if (key) errs[key] = issue.message;
      }
      setErrors(errs);
      return;
    }
    try {
      const id = await mutation.mutateAsync({
        applicantName: form.applicantName,
        email: form.email,
        phone: form.phone,
        birthDate: form.birthDate,
        businessName: form.businessName,
        businessNo: form.businessNo,
      });
      setSubmittedId(id);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "APPLY_002") {
          setErrors({ businessNo: "이미 신청된 사업자번호입니다" });
        } else {
          toast(err.message, "error");
        }
      } else {
        toast("신청 처리 중 오류가 발생했습니다", "error");
      }
    }
  };

  return (
    <Card
      style={{ width: 580, padding: "var(--space-8) var(--space-6)" }}
      title={null}
    >
      <div style={{ marginBottom: 8, fontSize: 12, color: "var(--fg-secondary)" }}>
        Butterfly
      </div>
      {submittedId !== null ? (
        <div style={{ textAlign: "center", padding: "32px 0" }}>
          <div
            style={{
              width: 56,
              height: 56,
              borderRadius: "50%",
              background: "var(--state-success-bg)",
              color: "var(--color-success)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              margin: "0 auto 16px",
              fontSize: 28,
            }}
          >
            ✓
          </div>
          <h2 style={{ fontSize: 20, margin: "0 0 6px" }}>
            신청이 접수되었습니다
          </h2>
          <p
            style={{
              color: "var(--fg-secondary)",
              fontSize: 13,
              margin: "0 0 20px",
            }}
          >
            신청 번호 #{submittedId}
            <br />
            관리자 승인 후 등록하신 이메일로 안내가 발송됩니다.
          </p>
          <Button
            variant="primaryStrong"
            size="md"
            onClick={() => navigate("/login")}
            style={{}}
          >
            로그인으로 이동
          </Button>
        </div>
      ) : (
        <>
          <h1 style={{ fontSize: 24, margin: "0 0 6px", fontWeight: 700 }}>
            서비스 신청서
          </h1>
          <p
            style={{
              color: "var(--fg-secondary)",
              fontSize: 13,
              margin: "0 0 16px",
              lineHeight: 1.6,
            }}
          >
            Butterfly는 신청서 검토를 거쳐 회사별로 OWNER 계정을 발급해 드리는
            B2B SaaS입니다. 승인이 완료되면 등록하신 이메일로 OWNER 비밀번호
            설정 링크와 회사 인증번호가 전달됩니다.
          </p>

          <div
            style={{
              background: "var(--color-primary-soft)",
              border: "1px solid var(--color-primary-100)",
              borderRadius: "var(--radius-md)",
              padding: "10px 12px",
              fontSize: 12,
              color: "var(--color-primary-900)",
              marginBottom: "var(--space-6)",
              lineHeight: 1.6,
            }}
          >
            <strong>처리 절차</strong>
            <br />
            ① 신청서 제출 → ② 운영진 검토(영업일 1~2일) → ③ OWNER 계정 자동 발급 →
            ④ 비밀번호 설정 후 로그인 → ⑤ 직원에게 회사 인증번호 공유
          </div>

          <form onSubmit={handleSubmit}>
            <SectionTitle>신청자 정보</SectionTitle>
            <div style={grid2()}>
              <Input
                label="이름"
                value={form.applicantName}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  update("applicantName", e.target.value)
                }
                error={errors.applicantName}
                style={{}}
              />
              <Input
                label="이메일"
                type="email"
                value={form.email}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  update("email", e.target.value)
                }
                error={errors.email}
                style={{}}
              />
              <Input
                label="전화번호"
                value={form.phone}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  update("phone", e.target.value)
                }
                placeholder="01012345678"
                error={errors.phone}
                style={{}}
              />
              <BirthDatePicker
                value={form.birthDate}
                onChange={(v: string) => update("birthDate", v)}
                error={errors.birthDate}
              />
            </div>

            <div style={{ height: "var(--space-5)" }} />
            <SectionTitle>사업자 정보</SectionTitle>
            <div style={grid2()}>
              <Input
                label="사업자명"
                value={form.businessName}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  update("businessName", e.target.value)
                }
                error={errors.businessName}
                style={{}}
              />
              <Input
                label="사업자번호"
                value={form.businessNo}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  update("businessNo", e.target.value)
                }
                placeholder="1234567890"
                help="10자리 숫자"
                error={errors.businessNo}
                style={{}}
              />
            </div>

            <div style={{ height: "var(--space-5)" }} />
            <Checkbox
              checked={form.agreed}
              onChange={(checked: boolean) => update("agreed", checked)}
              label="개인정보 처리방침에 동의합니다"
              style={{}}
            />
            {errors.agreed && (
              <div
                style={{
                  color: "var(--color-error)",
                  fontSize: 12,
                  marginTop: 6,
                }}
              >
                {errors.agreed}
              </div>
            )}

            <div style={{ height: "var(--space-6)" }} />
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
              {mutation.isPending ? "신청 중..." : "신청하기"}
            </Button>

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
          </form>
        </>
      )}
    </Card>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        fontSize: 13,
        fontWeight: 600,
        color: "var(--fg-strong)",
        marginBottom: 12,
        paddingBottom: 6,
        borderBottom: "1px solid var(--border-default)",
      }}
    >
      {children}
    </div>
  );
}

function grid2(): React.CSSProperties {
  return {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    gap: "var(--space-4)",
  };
}

