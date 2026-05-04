import { useEffect, useState } from "react";
import { Button, Input, Tag } from "@/components/common";
import {
  useSendCodeMutation,
  useVerifyCodeMutation,
} from "@/hooks/queries/auth";
import { mapAuthError } from "@/lib/auth-errors";
import { toast } from "@/lib/ui-bus";
import {
  emailSchema,
  emailVerifyCodeSchema,
} from "@/schemas/auth";

export interface EmailVerifyStepValue {
  email: string;
  verified: boolean;
}

interface Props {
  value: EmailVerifyStepValue;
  onChange: (next: EmailVerifyStepValue) => void;
}

const COOLDOWN_SECONDS = 60;

export default function EmailVerifyStep({ value, onChange }: Props) {
  const [code, setCode] = useState("");
  const [emailError, setEmailError] = useState<string | null>(null);
  const [codeError, setCodeError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(0);
  const [codeSent, setCodeSent] = useState(false);

  const sendMutation = useSendCodeMutation();
  const verifyMutation = useVerifyCodeMutation();

  // 쿨다운 인터벌
  useEffect(() => {
    if (cooldown <= 0) return;
    const id = window.setInterval(() => {
      setCooldown((c) => (c <= 1 ? 0 : c - 1));
    }, 1000);
    return () => window.clearInterval(id);
  }, [cooldown]);

  const handleSendCode = () => {
    setEmailError(null);
    const parsed = emailSchema.safeParse(value.email);
    if (!parsed.success) {
      setEmailError(parsed.error.issues[0]?.message ?? "이메일을 확인하세요");
      return;
    }
    sendMutation.mutate(
      { email: value.email },
      {
        onSuccess: () => {
          setCodeSent(true);
          setCooldown(COOLDOWN_SECONDS);
          toast("인증 코드를 발송했습니다", "success");
        },
        onError: (err) => {
          const mapped = mapAuthError(err);
          if (mapped.field === "email") setEmailError(mapped.message);
          else toast(mapped.message, "error");
        },
      },
    );
  };

  const handleVerify = () => {
    setCodeError(null);
    const parsed = emailVerifyCodeSchema.safeParse(code);
    if (!parsed.success) {
      setCodeError(parsed.error.issues[0]?.message ?? "코드를 확인하세요");
      return;
    }
    verifyMutation.mutate(
      { email: value.email, code },
      {
        onSuccess: () => {
          onChange({ ...value, verified: true });
          toast("이메일 인증 완료", "success");
        },
        onError: (err) => {
          const mapped = mapAuthError(err);
          if (mapped.field === "code") setCodeError(mapped.message);
          else toast(mapped.message, "error");
        },
      },
    );
  };

  return (
    <div>
      <div style={{ marginBottom: "var(--space-4)" }}>
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
          <div style={{ flex: 1 }}>
            <Input
              label="이메일"
              type="email"
              autoFocus
              value={value.email}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                onChange({ email: e.target.value, verified: false })
              }
              placeholder="you@example.com"
              autoComplete="email"
              readOnly={value.verified}
              disabled={value.verified}
              error={!!emailError}
              help={emailError ?? undefined}
            />
          </div>
          <div style={{ paddingBottom: emailError ? 22 : 0 }}>
            <Button
              type="button"
              variant="secondary"
              size="md"
              onClick={handleSendCode}
              disabled={
                value.verified || sendMutation.isPending || cooldown > 0
              }
              style={{
                opacity:
                  value.verified || sendMutation.isPending || cooldown > 0
                    ? 0.5
                    : 1,
                cursor:
                  value.verified || sendMutation.isPending || cooldown > 0
                    ? "not-allowed"
                    : "pointer",
                whiteSpace: "nowrap",
              }}
            >
              {sendMutation.isPending
                ? "발송 중..."
                : cooldown > 0
                  ? `재전송 (${cooldown}s)`
                  : codeSent
                    ? "재전송"
                    : "코드받기"}
            </Button>
          </div>
        </div>
      </div>

      {value.verified && (
        <Tag tone="success" dot>
          인증 완료
        </Tag>
      )}

      {codeSent && !value.verified && (
        <div style={{ marginTop: "var(--space-4)" }}>
          <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
            <div style={{ flex: 1 }}>
              <Input
                label="인증 코드 (6자리)"
                value={code}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  setCode(e.target.value.replace(/\s+/g, ""))
                }
                placeholder="abc123"
                maxLength={6}
                autoComplete="one-time-code"
                error={!!codeError}
                help={codeError ?? "메일로 전송된 6자리 코드를 입력하세요"}
              />
            </div>
            <div style={{ paddingBottom: codeError ? 22 : 22 }}>
              <Button
                type="button"
                variant="primaryStrong"
                size="md"
                onClick={handleVerify}
                disabled={verifyMutation.isPending}
                style={{
                  opacity: verifyMutation.isPending ? 0.5 : 1,
                  cursor: verifyMutation.isPending ? "not-allowed" : "pointer",
                  whiteSpace: "nowrap",
                }}
              >
                {verifyMutation.isPending ? "확인 중..." : "인증하기"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
