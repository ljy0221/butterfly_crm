import { Checkbox, Icon, Input } from "@/components/common";
import { useState } from "react";

export interface PasswordStepValue {
  password: string;
  passwordConfirm: string;
  agreedToTerms: boolean;
}

export interface PasswordStepErrors {
  password?: string;
  passwordConfirm?: string;
  agreedToTerms?: string;
}

interface Props {
  value: PasswordStepValue;
  onChange: (next: PasswordStepValue) => void;
  errors?: PasswordStepErrors;
}

export default function PasswordStep({ value, onChange, errors }: Props) {
  const [showPw, setShowPw] = useState(false);
  const [showPwConfirm, setShowPwConfirm] = useState(false);

  return (
    <div>
      <div style={{ position: "relative" }}>
        <Input
          label="비밀번호 (8~20자, 영소문자+숫자)"
          type={showPw ? "text" : "password"}
          value={value.password}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            onChange({ ...value, password: e.target.value })
          }
          placeholder="••••••••"
          autoComplete="new-password"
          autoFocus
          error={!!errors?.password}
          help={errors?.password}
        />
        <button
          type="button"
          aria-label={showPw ? "비밀번호 숨기기" : "비밀번호 표시"}
          onClick={() => setShowPw((v) => !v)}
          style={iconBtnStyle}
        >
          {showPw ? Icon.eyeOff(14) : Icon.eye(14)}
        </button>
      </div>

      <div style={{ height: "var(--space-3)" }} />

      <div style={{ position: "relative" }}>
        <Input
          label="비밀번호 확인"
          type={showPwConfirm ? "text" : "password"}
          value={value.passwordConfirm}
          onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
            onChange({ ...value, passwordConfirm: e.target.value })
          }
          placeholder="••••••••"
          autoComplete="new-password"
          error={!!errors?.passwordConfirm}
          help={errors?.passwordConfirm}
        />
        <button
          type="button"
          aria-label={showPwConfirm ? "비밀번호 숨기기" : "비밀번호 표시"}
          onClick={() => setShowPwConfirm((v) => !v)}
          style={iconBtnStyle}
        >
          {showPwConfirm ? Icon.eyeOff(14) : Icon.eye(14)}
        </button>
      </div>

      <div style={{ height: "var(--space-5)" }} />

      <Checkbox
        checked={value.agreedToTerms}
        onChange={(checked: boolean) =>
          onChange({ ...value, agreedToTerms: checked })
        }
        label="이용약관 및 개인정보 처리방침에 동의합니다"
      />
      {errors?.agreedToTerms && (
        <div
          style={{
            marginTop: 6,
            fontSize: 11,
            color: "var(--color-error)",
          }}
        >
          {errors.agreedToTerms}
        </div>
      )}
    </div>
  );
}

const iconBtnStyle: React.CSSProperties = {
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
};
