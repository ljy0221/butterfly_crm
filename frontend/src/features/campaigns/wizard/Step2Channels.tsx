import type { Channel } from "@/schemas/campaign";
import type { FieldErrorMap, WizardDraft } from "./wizardReducer";

/**
 * Step 2 — 채널 선택. 단일 선택 강제 (백엔드 entity `channel` 단수).
 * PUSH/EMAIL P0, SMS/WEBHOOK/KAKAO 활성화하되 시각적으로 동등 (실 연동은 백엔드 결합 후).
 */

interface ChannelOption {
  value: Channel;
  title: string;
  description: string;
  badge?: string;
  emoji: string;
}

const OPTIONS: ChannelOption[] = [
  { value: "PUSH", title: "PUSH", description: "앱 푸시 알림 (FCM)", emoji: "📱" },
  { value: "EMAIL", title: "EMAIL", description: "이메일 발송", emoji: "✉️" },
  { value: "SMS", title: "SMS", description: "단문 문자", badge: "P1", emoji: "💬" },
  { value: "WEBHOOK", title: "WEBHOOK", description: "외부 시스템 연동", badge: "P1", emoji: "🔗" },
  { value: "KAKAO", title: "KAKAO", description: "카카오 알림톡", badge: "P1", emoji: "🟡" },
];

export interface Step2ChannelsProps {
  draft: WizardDraft;
  errors: FieldErrorMap;
  onChange: (channel: Channel) => void;
}

export default function Step2Channels({ draft, errors, onChange }: Step2ChannelsProps) {
  return (
    <div>
      <h2 style={titleStyle}>채널 선택</h2>
      <p style={subtitleStyle}>
        메시지를 발송할 채널을 선택하세요. 한 캠페인은 하나의 채널만 사용할 수 있습니다.
      </p>

      {errors.channel && (
        <div role="alert" style={errorBanner}>
          {errors.channel}
        </div>
      )}

      <div
        role="radiogroup"
        aria-label="발송 채널 선택"
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
          gap: 12,
        }}
      >
        {OPTIONS.map((opt) => {
          const active = draft.channel === opt.value;
          return (
            <button
              key={opt.value}
              type="button"
              role="radio"
              aria-checked={active}
              onClick={() => onChange(opt.value)}
              style={{
                position: "relative",
                padding: 16,
                borderRadius: 12,
                border: "1px solid",
                borderColor: active ? "#3b5bdb" : "#e2e8f0",
                background: active ? "rgba(59,91,219,0.06)" : "#fff",
                textAlign: "left",
                cursor: "pointer",
                fontFamily: "inherit",
                display: "flex",
                flexDirection: "column",
                gap: 6,
                transition: "border-color 120ms",
              }}
            >
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ fontSize: 22 }} aria-hidden="true">
                  {opt.emoji}
                </span>
                <span
                  style={{
                    fontSize: 13.5,
                    fontWeight: 700,
                    color: active ? "#3b5bdb" : "#1f2937",
                  }}
                >
                  {opt.title}
                </span>
                {opt.badge && (
                  <span
                    style={{
                      marginLeft: "auto",
                      fontSize: 10,
                      padding: "1px 6px",
                      borderRadius: 4,
                      background: "#f1f5f9",
                      color: "#94a3b8",
                      fontWeight: 600,
                    }}
                  >
                    {opt.badge}
                  </span>
                )}
              </div>
              <div style={{ fontSize: 11.5, color: "#64748b", lineHeight: 1.5 }}>
                {opt.description}
              </div>
            </button>
          );
        })}
      </div>
    </div>
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
  marginBottom: 20,
  fontSize: 13,
  color: "#64748b",
  lineHeight: 1.5,
};

const errorBanner: React.CSSProperties = {
  marginBottom: 14,
  padding: 10,
  background: "rgba(239,68,68,.08)",
  border: "1px solid rgba(239,68,68,.25)",
  borderRadius: 8,
  color: "#b91c1c",
  fontSize: 12.5,
};
