import { useTemplateDetailQuery } from "@/hooks/queries/template";
import { describeRecurring } from "@/lib/cron-preview";
import {
  fromUtcIsoToLocal,
  type ScheduleInput,
} from "@/schemas/campaign";
import MobilePreviewPane from "../MobilePreviewPane";
import type { WizardDraft, WizardStep } from "./wizardReducer";

/**
 * Step 6 — 확인 + 저장.
 * 좌측 요약 카드 + 우측 MobilePreviewPane.
 * 각 항목 [편집] → onJump(step).
 */

export interface Step6ReviewProps {
  projectId: number;
  draft: WizardDraft;
  onJump: (step: WizardStep) => void;
}

export default function Step6Review({
  projectId,
  draft,
  onJump,
}: Step6ReviewProps) {
  const { data: template } = useTemplateDetailQuery(
    projectId,
    draft.messageTemplateId,
    !!draft.messageTemplateId,
  );

  const scheduleLabel = describeSchedule(draft.schedule);
  const estimatedTarget = draft.segmentId == null ? "전체 사용자" : draft.segmentName ?? `세그먼트 #${draft.segmentId}`;

  return (
    <div>
      <h2 style={titleStyle}>확인 + 저장</h2>
      <p style={subtitleStyle}>
        설정한 내용을 검토한 후 저장 또는 즉시 시작할 수 있습니다.
      </p>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16, alignItems: "start" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
          <ReviewSection title="기본 정보" onEdit={() => onJump(1)}>
            <ReviewRow label="이름" value={draft.campaignName || "(미입력)"} />
            <ReviewRow label="유형" value={draft.campaignType === "ONE_TIME" ? "일회성 (One-time)" : "자동화 (Automation)"} />
            <ReviewRow label="설명" value={draft.campaignDescription || "(미입력)"} />
          </ReviewSection>

          <ReviewSection title="채널" onEdit={() => onJump(2)}>
            <ReviewRow label="발송 채널" value={draft.channel} />
          </ReviewSection>

          <ReviewSection title="대상 세그먼트" onEdit={() => onJump(3)}>
            <ReviewRow label="발송 대상" value={estimatedTarget} />
          </ReviewSection>

          <ReviewSection title="발송 일정" onEdit={() => onJump(4)}>
            <ReviewRow label="발송 모드" value={scheduleLabel.modeLabel} />
            {scheduleLabel.detail && (
              <ReviewRow label="상세" value={scheduleLabel.detail} />
            )}
          </ReviewSection>

          <ReviewSection title="메시지 템플릿" onEdit={() => onJump(5)}>
            <ReviewRow label="템플릿" value={draft.messageTemplateName ?? "(미선택)"} />
            {template && (
              <ReviewRow
                label="감지된 변수"
                value={
                  template.requiredVariables.length === 0
                    ? "없음"
                    : template.requiredVariables.map((v) => `{{${v}}}`).join(" ")
                }
              />
            )}
          </ReviewSection>

          <CostEstimateCard draft={draft} />
        </div>

        <div style={{ position: "sticky", top: 16 }}>
          {template ? (
            <MobilePreviewPane
              channel={draft.channel}
              title={template.title ?? template.description}
              body={template.messageTemplateBody}
            />
          ) : (
            <div
              style={{
                background: "#fff",
                border: "1px dashed #e2e8f0",
                borderRadius: 12,
                padding: 32,
                textAlign: "center",
                color: "#94a3b8",
                fontSize: 12,
              }}
            >
              템플릿이 선택되지 않았습니다.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function describeSchedule(s: ScheduleInput): { modeLabel: string; detail: string } {
  if (s.type === "immediate") return { modeLabel: "즉시 발송", detail: "" };
  if (s.type === "scheduled") {
    const local = s.scheduledAt && s.scheduledAt.includes("Z")
      ? fromUtcIsoToLocal(s.scheduledAt)
      : s.scheduledAt;
    return { modeLabel: "예약 발송", detail: local || "(시각 미입력)" };
  }
  // recurring
  const detail = describeRecurring({
    repeatType: s.repeatType,
    repeatTime: s.repeatTime,
    repeatDayOfWeek: s.repeatDayOfWeek,
    repeatDayOfMonth: s.repeatDayOfMonth,
  });
  return {
    modeLabel: "반복 발송",
    detail: `${detail || "(설정 미완)"} · ${s.recurringCount}회`,
  };
}

function ReviewSection({
  title,
  onEdit,
  children,
}: {
  title: string;
  onEdit: () => void;
  children: React.ReactNode;
}) {
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 12,
        padding: 14,
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "baseline",
          justifyContent: "space-between",
          marginBottom: 10,
        }}
      >
        <h3 style={{ margin: 0, fontSize: 13, fontWeight: 700, color: "#1f2937" }}>
          {title}
        </h3>
        <button
          type="button"
          onClick={onEdit}
          style={{
            fontSize: 11.5,
            padding: "4px 10px",
            background: "transparent",
            color: "#3b5bdb",
            border: "1px solid #3b5bdb",
            borderRadius: 6,
            cursor: "pointer",
            fontFamily: "inherit",
            fontWeight: 600,
          }}
        >
          편집
        </button>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>{children}</div>
    </div>
  );
}

function ReviewRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "100px 1fr", gap: 12, fontSize: 12.5, lineHeight: 1.5 }}>
      <div style={{ color: "#94a3b8" }}>{label}</div>
      <div style={{ color: "#1f2937", wordBreak: "break-word" }}>{value}</div>
    </div>
  );
}

function CostEstimateCard({ draft }: { draft: WizardDraft }) {
  // mock: 채널별 단가 추정
  const unit =
    draft.channel === "EMAIL"
      ? 5
      : draft.channel === "SMS"
        ? 18
        : draft.channel === "PUSH"
          ? 1
          : draft.channel === "KAKAO"
            ? 12
            : 2;
  const target = draft.segmentId == null ? 25000 : 1500;
  const estimate = unit * target;
  return (
    <div
      style={{
        background: "linear-gradient(135deg, rgba(59,91,219,0.06) 0%, rgba(99,102,241,0.04) 100%)",
        border: "1px solid rgba(59,91,219,0.2)",
        borderRadius: 12,
        padding: 14,
      }}
    >
      <div style={{ fontSize: 11, fontWeight: 600, color: "#3b5bdb", textTransform: "uppercase", letterSpacing: "0.05em", marginBottom: 8 }}>
        예상 발송 비용 (mock)
      </div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 8 }}>
        <span style={{ fontSize: 22, fontWeight: 700, color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>
          ₩ {estimate.toLocaleString()}
        </span>
        <span style={{ fontSize: 11, color: "#64748b" }}>
          ({target.toLocaleString()}건 × ₩{unit})
        </span>
      </div>
      <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 6 }}>
        실제 발송 비용은 채널 사업자/계약 단가에 따라 달라질 수 있습니다.
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
  marginBottom: 18,
  fontSize: 13,
  color: "#64748b",
  lineHeight: 1.5,
};
