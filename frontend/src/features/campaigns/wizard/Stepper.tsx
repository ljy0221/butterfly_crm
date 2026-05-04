import type { WizardStep } from "./wizardReducer";

/**
 * Sprint 6 — 좌측 sticky 진행 표시 stepper.
 * 6단계 + 마지막 review. step 클릭 시 onJump(step) 호출.
 */

const STEPS: Array<{ step: WizardStep; label: string; sublabel: string }> = [
  { step: 1, label: "기본 정보", sublabel: "이름·유형·설명" },
  { step: 2, label: "채널 선택", sublabel: "PUSH / EMAIL ..." },
  { step: 3, label: "세그먼트 선택", sublabel: "타겟 또는 전체" },
  { step: 4, label: "발송 일정", sublabel: "즉시·예약·반복" },
  { step: 5, label: "메시지 템플릿", sublabel: "본문 + 미리보기" },
  { step: 6, label: "확인 + 저장", sublabel: "최종 점검" },
];

export interface StepperProps {
  current: WizardStep;
  /** 사용자가 통과한 최대 step (이전 step 자유 점프 허용). */
  furthest: WizardStep;
  onJump: (step: WizardStep) => void;
}

export default function Stepper({ current, furthest, onJump }: StepperProps) {
  return (
    <nav
      aria-label="캠페인 위저드 진행 단계"
      style={{
        position: "sticky",
        top: 24,
        background: "#fff",
        border: "1px solid var(--border-default, #e2e8f0)",
        borderRadius: 12,
        padding: 16,
        width: 240,
        flexShrink: 0,
      }}
    >
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          textTransform: "uppercase",
          letterSpacing: "0.05em",
          color: "#94a3b8",
          marginBottom: 12,
        }}
      >
        진행 단계 ({current} / 6)
      </div>
      <ol
        role="list"
        style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 4 }}
      >
        {STEPS.map(({ step, label, sublabel }) => {
          const active = step === current;
          const done = step < current || step <= furthest;
          const clickable = step <= furthest;
          return (
            <li key={step}>
              <button
                type="button"
                disabled={!clickable}
                aria-current={active ? "step" : undefined}
                onClick={() => clickable && onJump(step)}
                style={{
                  width: "100%",
                  textAlign: "left",
                  padding: "10px 10px",
                  borderRadius: 8,
                  border: "1px solid",
                  borderColor: active ? "#3b5bdb" : "transparent",
                  background: active ? "rgba(59,91,219,0.06)" : "transparent",
                  color: active
                    ? "#3b5bdb"
                    : done
                      ? "#1f2937"
                      : "#94a3b8",
                  cursor: clickable ? "pointer" : "default",
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  fontFamily: "inherit",
                  transition: "background 120ms",
                }}
                onMouseEnter={(e) => {
                  if (!active && clickable) {
                    e.currentTarget.style.background = "#f8fafc";
                  }
                }}
                onMouseLeave={(e) => {
                  if (!active) {
                    e.currentTarget.style.background = "transparent";
                  }
                }}
              >
                <span
                  aria-hidden="true"
                  style={{
                    flexShrink: 0,
                    width: 24,
                    height: 24,
                    borderRadius: "50%",
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontSize: 11,
                    fontWeight: 700,
                    background: active
                      ? "#3b5bdb"
                      : done
                        ? "#16a34a"
                        : "#e2e8f0",
                    color: active ? "#fff" : done ? "#fff" : "#94a3b8",
                  }}
                >
                  {done && !active ? "✓" : step}
                </span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 12.5, fontWeight: 600 }}>{label}</div>
                  <div
                    style={{
                      fontSize: 11,
                      color: active ? "#3b5bdb" : "#94a3b8",
                      marginTop: 2,
                    }}
                  >
                    {sublabel}
                  </div>
                </div>
              </button>
            </li>
          );
        })}
      </ol>

      <div
        role="progressbar"
        aria-valuemin={1}
        aria-valuemax={6}
        aria-valuenow={current}
        style={{
          marginTop: 14,
          height: 4,
          background: "#e2e8f0",
          borderRadius: 2,
          overflow: "hidden",
        }}
      >
        <div
          style={{
            width: `${((current - 1) / 5) * 100}%`,
            height: "100%",
            background: "#3b5bdb",
            transition: "width 240ms ease",
          }}
        />
      </div>
    </nav>
  );
}
