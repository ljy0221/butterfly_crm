import type { CampaignType } from "@/schemas/campaign";
import type { FieldErrorMap, WizardDraft } from "./wizardReducer";

/**
 * Step 1 — 기본 정보. 이름·유형·설명.
 */

export interface Step1BasicProps {
  draft: WizardDraft;
  errors: FieldErrorMap;
  onChangeName: (v: string) => void;
  onChangeDescription: (v: string) => void;
  onChangeType: (v: CampaignType) => void;
}

export default function Step1Basic({
  draft,
  errors,
  onChangeName,
  onChangeDescription,
  onChangeType,
}: Step1BasicProps) {
  return (
    <div>
      <h2 style={titleStyle}>기본 정보</h2>
      <p style={subtitleStyle}>캠페인 이름과 유형을 입력하세요.</p>

      <Field label="캠페인 이름" required error={errors.campaignName}>
        <input
          autoFocus
          value={draft.campaignName}
          onChange={(e) => onChangeName(e.target.value)}
          maxLength={50}
          placeholder="예: 여름 시즌 프로모션"
          style={inputStyle(!!errors.campaignName)}
        />
        <Help>{`${draft.campaignName.length}/50`}</Help>
      </Field>

      <Field label="캠페인 유형" required>
        <div role="radiogroup" aria-label="캠페인 유형 선택" style={{ display: "flex", gap: 8 }}>
          <TypeCard
            value="ONE_TIME"
            current={draft.campaignType}
            onSelect={onChangeType}
            title="일회성 (One-time)"
            description="단일 발송 또는 예약/반복 발송"
            disabled={false}
          />
          <TypeCard
            value="AUTOMATION"
            current={draft.campaignType}
            onSelect={onChangeType}
            title="자동화 (Automation)"
            description="이벤트 기반 트리거 발송 (P1)"
            disabled={true}
          />
        </div>
      </Field>

      <Field label="설명" required error={errors.campaignDescription}>
        <textarea
          value={draft.campaignDescription}
          onChange={(e) => onChangeDescription(e.target.value)}
          maxLength={200}
          rows={4}
          placeholder="이 캠페인의 목적과 발송 컨텍스트를 간단히 메모하세요."
          style={{
            ...inputStyle(!!errors.campaignDescription),
            height: "auto",
            minHeight: 96,
            resize: "vertical",
            padding: 12,
            lineHeight: 1.55,
          }}
        />
        <Help>{`${draft.campaignDescription.length}/200`}</Help>
      </Field>
    </div>
  );
}

function TypeCard({
  value,
  current,
  onSelect,
  title,
  description,
  disabled,
}: {
  value: CampaignType;
  current: CampaignType;
  onSelect: (v: CampaignType) => void;
  title: string;
  description: string;
  disabled: boolean;
}) {
  const active = value === current;
  return (
    <button
      type="button"
      role="radio"
      aria-checked={active}
      disabled={disabled}
      onClick={() => !disabled && onSelect(value)}
      style={{
        flex: 1,
        textAlign: "left",
        padding: 14,
        borderRadius: 10,
        border: "1px solid",
        borderColor: active ? "#3b5bdb" : "#e2e8f0",
        background: active ? "rgba(59,91,219,0.06)" : "#fff",
        opacity: disabled ? 0.5 : 1,
        cursor: disabled ? "not-allowed" : "pointer",
        fontFamily: "inherit",
      }}
    >
      <div
        style={{
          fontSize: 13,
          fontWeight: 700,
          color: active ? "#3b5bdb" : "#1f2937",
          marginBottom: 4,
        }}
      >
        {title}
        {disabled && (
          <span
            style={{
              marginLeft: 8,
              fontSize: 10,
              padding: "1px 6px",
              borderRadius: 4,
              background: "#f1f5f9",
              color: "#94a3b8",
            }}
          >
            P1
          </span>
        )}
      </div>
      <div style={{ fontSize: 11.5, color: "#64748b", lineHeight: 1.45 }}>{description}</div>
    </button>
  );
}

const titleStyle: React.CSSProperties = {
  margin: 0,
  marginBottom: 6,
  fontSize: 18,
  fontWeight: 700,
  color: "#1f2937",
  fontFamily: "var(--font-display)",
};

const subtitleStyle: React.CSSProperties = {
  margin: 0,
  marginBottom: 24,
  fontSize: 13,
  color: "#64748b",
  lineHeight: 1.5,
};

function Field({
  label,
  required,
  error,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 18 }}>
      <div style={{ fontSize: 12, fontWeight: 500, color: "#334155", marginBottom: 6 }}>
        {label}
        {required && <span style={{ color: "#ef4444", marginLeft: 4 }}>*</span>}
      </div>
      {children}
      {error && (
        <div style={{ marginTop: 6, fontSize: 11, color: "#ef4444" }} role="alert">
          {error}
        </div>
      )}
    </div>
  );
}

function Help({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ marginTop: 4, fontSize: 11, color: "#94a3b8", textAlign: "right" }}>{children}</div>
  );
}

function inputStyle(invalid: boolean): React.CSSProperties {
  return {
    width: "100%",
    height: 36,
    padding: "0 12px",
    fontFamily: "inherit",
    fontSize: 13,
    color: "#1f2937",
    background: "#fff",
    border: `1px solid ${invalid ? "#ef4444" : "#e2e8f0"}`,
    borderRadius: 8,
    outline: "none",
    boxSizing: "border-box",
  };
}
