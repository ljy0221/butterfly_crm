import { useEffect, useReducer, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { Button, Icon, PageHeader } from "@/components/common";
import InfoTooltip from "@/components/common/InfoTooltip";
import { ApiError } from "@/api/types";
import { confirmDialog, openModal, toast } from "@/lib/ui-bus";
import { mapCampaignError } from "@/lib/campaign-errors";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import {
  useCampaignDetailQuery,
  useCreateCampaignMutation,
  useStartCampaignMutation,
  useUpdateCampaignMutation,
} from "@/hooks/queries/campaign";
import { useCreditBalanceQuery } from "@/hooks/queries/payment";
import { useCampaignDraftMutation } from "@/hooks/queries/ai";
import type { CampaignDraftResponse } from "@/api/ai";
import {
  step1Schema,
  step2Schema,
  step3Schema,
  step4Schema,
  step5Schema,
  toUtcIso,
} from "@/schemas/campaign";
import type { ScheduleInput } from "@/schemas/campaign";
import type { CreateCampaignRequest } from "@/api/campaign";
import {
  initialDraft,
  makeInitialState,
  wizardReducer,
  type FieldErrorMap,
  type WizardDraft,
  type WizardStep,
} from "./wizard/wizardReducer";
import Stepper from "./wizard/Stepper";
import Step1Basic from "./wizard/Step1Basic";
import Step2Channels from "./wizard/Step2Channels";
import Step3Segment from "./wizard/Step3Segment";
import Step4Schedule from "./wizard/Step4Schedule";
import Step5Template from "./wizard/Step5Template";
import Step6Review from "./wizard/Step6Review";
import TemplateFormModal, {
  type TemplateFormValues,
} from "@/features/templates/TemplateFormModal";
import AiLaunchButton from "@/features/ai/AiLaunchButton";

const DEFAULT_AI_TEMPLATE_VARIABLES = [
  "name",
  "email",
  "phone",
  "segmentName",
] as const;

/**
 * Sprint 6 — Campaign Wizard.
 *
 * 단일 라우트 + step state. mode="create" 는 /campaigns/new 진입,
 * mode="edit" 은 detail 화면에서 [수정] 클릭 시 호출 (URL 미변경) — 본 구현은 P0
 * 로 create 만 라우팅하고, edit 모드는 query string `?edit=1` 로 detail 와 동일 path 에서 진입한다.
 *
 * 핵심:
 *  - useReducer + step 별 부분 검증 (step1Schema~step5Schema)
 *  - [다음] 검증 실패 → setErrors + toast
 *  - 6단계: [DRAFT 저장] / [생성 후 시작]
 *  - beforeunload prompt + 페이지 이탈 confirm (dirty 추적)
 */

export interface CampaignWizardProps {
  mode: "create" | "edit";
}

function getFurthestStep(state: { step: WizardStep }): WizardStep {
  return state.step;
}

export default function CampaignWizard({ mode }: CampaignWizardProps) {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;
  const member = useAuthStore((s) => s.member);
  const navigate = useNavigate();
  const params = useParams<{ campaignId?: string }>();
  const editingCampaignId =
    mode === "edit" && params.campaignId ? Number(params.campaignId) : null;

  const [state, dispatch] = useReducer(
    wizardReducer,
    makeInitialState(mode, editingCampaignId),
  );
  const [furthest, setFurthest] = useState<WizardStep>(1);
  const [submitting, setSubmitting] = useState(false);

  const isOwner = role === "OWNER";
  const balanceQuery = useCreditBalanceQuery(projectId, isOwner && state.step === 6);
  const isInsufficientBalance = (() => {
    if (!isOwner) return false;
    const available = balanceQuery.data?.availableBalance;
    const cost = state.draft.estimatedAudienceCount;
    return available != null && cost != null && available < cost;
  })();
  const isZeroAudience = state.draft.estimatedAudienceCount === 0;

  const { data: editTarget } = useCampaignDetailQuery(
    projectId,
    editingCampaignId,
    mode === "edit" && projectId != null && editingCampaignId != null,
  );

  // edit 모드 hydrate
  // ⚠️ 백엔드 CampaignDetailResponse 에 schedule / segmentName / messageTemplateName 미노출 (D-1 보류).
  // schedule 은 위저드 default(immediate), 이름들은 빈 문자열로 두고 사용자가 재선택하도록 유도.
  useEffect(() => {
    if (mode !== "edit" || !editTarget) return;
    const draft: WizardDraft = {
      ...initialDraft,
      campaignName: editTarget.campaignName,
      campaignDescription: editTarget.campaignDescription,
      campaignType: editTarget.campaignType,
      channel: editTarget.channel,
      segmentId: editTarget.segmentId,
      segmentName: null,
      schedule: { type: "immediate" },
      messageTemplateId: editTarget.messageTemplateId,
      messageTemplateName: null,
      dirty: false,
    };
    dispatch({ type: "hydrate", draft, campaignId: editTarget.campaignId });
  }, [mode, editTarget]);

  // beforeunload prompt
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent): void => {
      if (state.draft.dirty || (mode === "create" && state.step > 1)) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [state.draft.dirty, state.step, mode]);

  const createMut = useCreateCampaignMutation(projectId ?? -1);
  const updateMut = useUpdateCampaignMutation(projectId ?? -1);
  const startMut = useStartCampaignMutation(projectId ?? -1);

  const validateStep = (step: WizardStep): { ok: boolean; errors: FieldErrorMap } => {
    const d = state.draft;
    if (step === 1) {
      const r = step1Schema.safeParse({
        campaignName: d.campaignName.trim(),
        campaignType: d.campaignType,
        campaignDescription: d.campaignDescription.trim(),
      });
      return zodResult(r);
    }
    if (step === 2) {
      return zodResult(step2Schema.safeParse({ channel: d.channel }));
    }
    if (step === 3) {
      return zodResult(step3Schema.safeParse({ segmentId: d.segmentId }));
    }
    if (step === 4) {
      // Schedule discriminated union 검증 — scheduled 인 경우 scheduledAt 빈문자열 거부
      const r = step4Schema.safeParse({ schedule: d.schedule });
      return zodResult(r);
    }
    if (step === 5) {
      return zodResult(step5Schema.safeParse({ messageTemplateId: d.messageTemplateId }));
    }
    return { ok: true, errors: {} };
  };

  const onNext = (): void => {
    const r = validateStep(state.step);
    if (!r.ok) {
      dispatch({ type: "set/errors", errors: r.errors });
      toast("필수 항목을 확인해주세요", "warning");
      return;
    }
    dispatch({ type: "set/errors", errors: {} });
    const nextStep = (state.step + 1) as WizardStep;
    setFurthest((f) => (nextStep > f ? nextStep : f));
    dispatch({ type: "next" });
  };

  const onPrev = (): void => {
    dispatch({ type: "prev" });
  };

  const openCampaignCopilot = (): void => {
    if (projectId == null) return;
    openModal(({ close }) => (
      <CampaignCopilotModal
        projectId={projectId}
        draft={state.draft}
        onApplyCampaign={onApplyCampaignDraft}
        onOpenTemplate={onOpenTemplateDraft}
        onClose={close}
      />
    ));
  };

  const onApplyCampaignDraft = (draft: CampaignDraftResponse): void => {
    const c = draft.campaign;
    if (c.campaignName) {
      dispatch({ type: "set/name", value: c.campaignName.slice(0, 50) });
    }
    if (c.campaignDescription) {
      dispatch({
        type: "set/description",
        value: c.campaignDescription.slice(0, 200),
      });
    }
    if (c.campaignType) {
      dispatch({ type: "set/type", value: c.campaignType });
    }
    if (c.channel) {
      dispatch({ type: "set/channel", value: c.channel });
    }
    dispatch({ type: "clear/errors" });
    toast("AI 초안을 기본 정보에 적용했습니다.", "success");
  };

  const onOpenTemplateDraft = (draft: CampaignDraftResponse): void => {
    if (projectId == null) return;
    const template: TemplateFormValues = {
      messageTemplateName: draft.template.messageTemplateName,
      messageTemplateDescription: draft.template.messageTemplateDescription,
      channel: draft.template.channel,
      messageTemplateTitle: draft.template.messageTemplateTitle,
      messageTemplateBody: draft.template.messageTemplateBody,
      messageTemplateImageUrl:
        draft.template.messageTemplateImageUrl ?? undefined,
    };
    openModal(({ close }) => (
      <TemplateFormModal
        mode="create"
        projectId={projectId}
        initialValues={template}
        onClose={close}
      />
    ));
  };

  const onJump = (target: WizardStep): void => {
    if (target <= state.step) {
      dispatch({ type: "set/step", step: target });
      return;
    }
    // 미래 step 으로 점프 시 — 현재 step 까지 검증 통과한 경우만 허용
    for (let s = state.step; s < target; s++) {
      const r = validateStep(s as WizardStep);
      if (!r.ok) {
        dispatch({ type: "set/step", step: s as WizardStep });
        dispatch({ type: "set/errors", errors: r.errors });
        toast("필수 항목을 확인해주세요", "warning");
        return;
      }
    }
    setFurthest((f) => (target > f ? target : f));
    dispatch({ type: "set/step", step: target });
  };

  const finalizeCreate = async (startAfter: boolean): Promise<void> => {
    if (projectId == null) return;
    // 6단계 종합 검증
    for (let s = 1 as WizardStep; s <= 5; s = (s + 1) as WizardStep) {
      const r = validateStep(s);
      if (!r.ok) {
        dispatch({ type: "set/step", step: s });
        dispatch({ type: "set/errors", errors: r.errors });
        toast("필수 항목을 확인해주세요", "warning");
        return;
      }
    }

    if (startAfter && state.draft.estimatedAudienceCount === 0) {
      toast("발송 대상이 0명입니다. 세그먼트를 변경하거나 빌드 후 다시 시도해주세요.", "error");
      dispatch({ type: "set/step", step: 3 });
      return;
    }

    if (startAfter) {
      const ok = await confirmDialog({
        title: "캠페인을 시작하시겠습니까?",
        message:
          state.draft.schedule.type === "immediate"
            ? "발송이 즉시 시작됩니다. 시작 후에는 일시정지/종료만 가능합니다."
            : "캠페인이 ACTIVE 상태로 전환되어 예약/반복 발송이 활성화됩니다.",
        confirmText: state.draft.schedule.type === "immediate" ? "발송 시작" : "활성화",
        cancelText: "취소",
        tone: "danger",
      });
      if (!ok) return;
    }

    setSubmitting(true);
    try {
      // 백엔드 CampaignCreateRequest / CampaignUpdateRequest 는 4개 필드만 수신.
      // schedule / campaignType / channel / isTempSave 는 미지원 (D-1, D-4 보류).
      const req: CreateCampaignRequest = {
        campaignName: state.draft.campaignName.trim(),
        campaignDescription: state.draft.campaignDescription.trim(),
        messageTemplateId: state.draft.messageTemplateId as number,
        segmentId: state.draft.segmentId,
      };

      let result;
      if (mode === "edit" && state.campaignId != null) {
        result = await updateMut.mutateAsync({
          campaignId: state.campaignId,
          req,
        });
      } else {
        result = await createMut.mutateAsync(req);
      }

      if (startAfter) {
        await startMut.mutateAsync(result.campaignId);
        toast("캠페인이 시작되었습니다", "success");
      } else {
        toast(mode === "edit" ? "캠페인이 수정되었습니다" : "캠페인이 저장되었습니다", "success");
      }
      navigate(`/p/${projectId}/campaigns/${result.campaignId}`);
    } catch (err) {
      const m = mapCampaignError(err);
      // 위저드 jumpStep 가 있으면 해당 step 으로 점프 + field error 노출
      if (m.jumpStep != null) {
        dispatch({ type: "set/step", step: m.jumpStep });
        if (m.field) {
          dispatch({ type: "set/errors", errors: { [m.field]: m.message } });
        }
      }
      const fallback =
        err instanceof ApiError ? err.message : err instanceof Error ? err.message : "저장 중 오류가 발생했습니다";
      toast(m.message ?? fallback, "error");
    } finally {
      setSubmitting(false);
    }
  };

  // 가드: 권한 부족
  if (mode === "create" && !canEdit) {
    return <Navigate to={`/p/${projectId}/campaigns`} replace />;
  }
  if (projectId == null) {
    return <Navigate to="/projects" replace />;
  }

  const renderStep = (): React.ReactNode => {
    switch (state.step) {
      case 1:
        return (
          <Step1Basic
            draft={state.draft}
            errors={state.errors}
            onChangeName={(v) => dispatch({ type: "set/name", value: v })}
            onChangeDescription={(v) => dispatch({ type: "set/description", value: v })}
            onChangeType={(v) => dispatch({ type: "set/type", value: v })}
          />
        );
      case 2:
        return (
          <Step2Channels
            draft={state.draft}
            errors={state.errors}
            onChange={(c) => dispatch({ type: "set/channel", value: c })}
          />
        );
      case 3:
        return (
          <Step3Segment
            projectId={projectId}
            draft={state.draft}
            errors={state.errors}
            onSelect={(id, name, estimatedCount) =>
              dispatch({ type: "set/segment", segmentId: id, segmentName: name, estimatedCount })
            }
          />
        );
      case 4:
        return (
          <Step4Schedule
            draft={state.draft}
            errors={state.errors}
            onChangeScheduleType={(t) => dispatch({ type: "set/scheduleType", value: t })}
            onChangeScheduledAt={(v) => dispatch({ type: "set/scheduledAt", value: v })}
            onChangeRepeatType={(v) => dispatch({ type: "set/repeatType", value: v })}
            onChangeRepeatTime={(v) => dispatch({ type: "set/repeatTime", value: v })}
            onChangeRepeatDayOfWeek={(v) => dispatch({ type: "set/repeatDayOfWeek", value: v })}
            onChangeRepeatDayOfMonth={(v) => dispatch({ type: "set/repeatDayOfMonth", value: v })}
            onChangeRecurringCount={(v) => dispatch({ type: "set/recurringCount", value: v })}
          />
        );
      case 5:
        return (
          <Step5Template
            projectId={projectId}
            draft={state.draft}
            errors={state.errors}
            onSelect={(id, name) =>
              dispatch({ type: "set/template", messageTemplateId: id, messageTemplateName: name })
            }
          />
        );
      case 6:
        return <Step6Review projectId={projectId} draft={state.draft} onJump={onJump} />;
      default:
        return null;
    }
  };

  const headerTitle = mode === "edit" ? "캠페인 수정" : "새 캠페인";
  const startAfterLabel =
    state.draft.schedule.type === "immediate"
      ? "생성 후 즉시 시작"
      : "생성 후 활성화";

  return (
    <div>
      <PageHeader
        title={headerTitle}
        subtitle="6단계로 메시지 캠페인을 설정합니다."
        actions={
          <Button
            variant="ghost"
            size="md"
            onClick={async () => {
              if (state.draft.dirty || state.step > 1) {
                const ok = await confirmDialog({
                  title: "위저드를 종료하시겠습니까?",
                  message: "저장하지 않은 변경 사항은 사라집니다.",
                  confirmText: "종료",
                  cancelText: "계속 편집",
                  tone: "danger",
                });
                if (!ok) return;
              }
              navigate(`/p/${projectId}/campaigns`);
            }}
            style={{}}
          >
            취소
          </Button>
        }
      />

      {canEdit && (
        <div style={{ marginBottom: 16, display: "flex", justifyContent: "flex-end" }}>
          <AiLaunchButton
            label="AI로 초안 만들기"
            caption="Campaign Copilot"
            onClick={openCampaignCopilot}
          />
        </div>
      )}

      <div style={{ display: "flex", gap: 20, alignItems: "flex-start" }}>
        <Stepper current={state.step} furthest={furthest} onJump={onJump} />

        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              background: "#fff",
              border: "1px solid var(--border-default, #e2e8f0)",
              borderRadius: 12,
              padding: 24,
            }}
          >
            {renderStep()}
          </div>

          <div
            style={{
              display: "flex",
              gap: 8,
              justifyContent: "space-between",
              alignItems: "center",
              marginTop: 16,
            }}
          >
            <Button
              variant="secondary"
              size="md"
              onClick={onPrev}
              disabled={state.step === 1 || submitting}
              style={{ visibility: state.step === 1 ? "hidden" : "visible" }}
            >
              ← 이전
            </Button>

            <div style={{ display: "flex", gap: 8 }}>
              {state.step < 6 && (
                <Button
                  variant="primaryStrong"
                  size="md"
                  onClick={onNext}
                  disabled={submitting}
                  style={{}}
                >
                  다음 →
                </Button>
              )}
              {state.step === 6 && (
                <>
                  <Button
                    variant="secondary"
                    size="md"
                    onClick={() => finalizeCreate(false)}
                    disabled={submitting}
                    style={{}}
                  >
                    {submitting ? "저장 중..." : "DRAFT로 저장"}
                  </Button>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <Button
                      variant="primaryStrong"
                      size="md"
                      onClick={() => finalizeCreate(true)}
                      disabled={submitting || isInsufficientBalance || isZeroAudience}
                      style={{}}
                    >
                      {submitting ? "처리 중..." : startAfterLabel}
                    </Button>
                    <InfoTooltip
                      message={
                        isZeroAudience
                          ? "발송 대상이 0명입니다. 세그먼트를 변경해주세요."
                          : isInsufficientBalance
                          ? "잔액 부족 — 충전 후 시작 가능합니다."
                          : undefined
                      }
                    />
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function CampaignCopilotModal({
  projectId,
  draft: wizardDraft,
  onApplyCampaign,
  onOpenTemplate,
  onClose,
}: {
  projectId: number;
  draft: WizardDraft;
  onApplyCampaign: (draft: CampaignDraftResponse) => void;
  onOpenTemplate: (draft: CampaignDraftResponse) => void;
  onClose: () => void;
}) {
  const [prompt, setPrompt] = useState("");
  const [draft, setDraft] = useState<CampaignDraftResponse | null>(null);
  const campaignDraftMut = useCampaignDraftMutation(projectId);

  const onGenerate = async (): Promise<void> => {
    const nextPrompt = prompt.trim();
    if (!nextPrompt) {
      toast("AI에게 맡길 캠페인 목표를 입력해 주세요.", "warning");
      return;
    }
    try {
      const res = await campaignDraftMut.mutateAsync({
        prompt: nextPrompt,
        availableVariables: [...DEFAULT_AI_TEMPLATE_VARIABLES],
        context: {
          campaignName: wizardDraft.campaignName,
          campaignDescription: wizardDraft.campaignDescription,
          campaignType: wizardDraft.campaignType,
          channel: wizardDraft.channel,
          segmentId: wizardDraft.segmentId,
          segmentName: wizardDraft.segmentName,
        },
      });
      setDraft(res);
      toast("AI 캠페인 초안을 만들었습니다. 적용 전 내용을 확인해 주세요.", "success");
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : "AI 캠페인 초안을 만들지 못했습니다.";
      toast(msg, "error");
    }
  };
  return (
    <section
      aria-label="캠페인 AI 초안"
      style={{
        width: "min(820px, calc(100vw - 48px))",
        background: "#fff",
        border: "1px solid rgba(99,102,241,.24)",
        borderRadius: 16,
        padding: 20,
        boxShadow: "0 28px 80px -24px rgba(15,23,42,.42)",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
        <div>
          <div style={{ fontSize: 16, fontWeight: 800, color: "#111827" }}>
            AI Campaign Copilot
          </div>
          <p style={{ margin: "5px 0 0", fontSize: 12, color: "#64748b" }}>
            목표를 입력하면 기본 정보와 메시지 템플릿 초안을 생성합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          style={{
            width: 30,
            height: 30,
            border: "1px solid #e2e8f0",
            borderRadius: 8,
            background: "#fff",
            color: "#64748b",
            cursor: "pointer",
            fontSize: 18,
            lineHeight: 1,
          }}
        >
          ×
        </button>
      </div>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(0, 1fr) auto",
          gap: 12,
          alignItems: "start",
          marginTop: 16,
        }}
      >
        <div>
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            rows={4}
            placeholder="예: 장바구니에 상품을 담고 결제하지 않은 고객에게 24시간 한정 쿠폰을 이메일로 안내"
            style={{
              width: "100%",
              resize: "vertical",
              minHeight: 72,
              padding: 12,
              fontFamily: "inherit",
              fontSize: 13,
              lineHeight: 1.5,
              color: "#1f2937",
              border: "1px solid #cbd5e1",
              borderRadius: 8,
              outline: "none",
              boxSizing: "border-box",
            }}
          />
        </div>
        <Button
          variant="primaryStrong"
          size="md"
          icon={Icon.send(13)}
          onClick={onGenerate}
          disabled={campaignDraftMut.isPending}
          style={{ marginTop: 0 }}
        >
          {campaignDraftMut.isPending ? "생성 중..." : "초안 생성"}
        </Button>
      </div>

      {draft && (
        <div
          style={{
            marginTop: 12,
            display: "grid",
            gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1.2fr)",
            gap: 10,
          }}
        >
          <div style={aiDraftCardStyle}>
            <div style={aiDraftTitleStyle}>기본 정보 초안</div>
            <div style={aiDraftLineStyle}>
              {draft.campaign.campaignName ?? "(이름 없음)"}
            </div>
            <div style={aiDraftSubStyle}>
              {draft.campaign.campaignDescription ?? "(설명 없음)"}
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => onApplyCampaign(draft)}
              style={{ marginTop: 10 }}
            >
              기본 정보에 적용
            </Button>
          </div>
          <div style={aiDraftCardStyle}>
            <div style={aiDraftTitleStyle}>템플릿 초안</div>
            <div style={aiDraftLineStyle}>{draft.template.messageTemplateTitle}</div>
            <div style={aiDraftSubStyle}>{draft.template.messageTemplateBody}</div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => onOpenTemplate(draft)}
              style={{ marginTop: 10 }}
            >
              새 템플릿으로 열기
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}

const aiDraftCardStyle: React.CSSProperties = {
  padding: 12,
  border: "1px solid #e2e8f0",
  borderRadius: 8,
  background: "#f8fafc",
  minWidth: 0,
};

const aiDraftTitleStyle: React.CSSProperties = {
  marginBottom: 6,
  fontSize: 12,
  fontWeight: 700,
  color: "#3b5bdb",
};

const aiDraftLineStyle: React.CSSProperties = {
  fontSize: 13,
  fontWeight: 700,
  color: "#1f2937",
  lineHeight: 1.45,
  overflowWrap: "anywhere",
};

const aiDraftSubStyle: React.CSSProperties = {
  marginTop: 4,
  fontSize: 12,
  color: "#64748b",
  lineHeight: 1.5,
  display: "-webkit-box",
  WebkitLineClamp: 3,
  WebkitBoxOrient: "vertical",
  overflow: "hidden",
};

// ---------- helpers ----------

import { z } from "zod";

function zodResult<T>(r: z.SafeParseReturnType<unknown, T>): {
  ok: boolean;
  errors: FieldErrorMap;
} {
  if (r.success) return { ok: true, errors: {} };
  const errors: FieldErrorMap = {};
  for (const issue of r.error.issues) {
    // schedule 의 경우 path = ["schedule", "scheduledAt"] 또는 ["schedule", "repeatType"] 등
    let key = issue.path[0] as string | undefined;
    if (key === "schedule" && issue.path.length > 1) {
      key = issue.path[1] as string;
    }
    if (key) errors[key] = issue.message;
  }
  return { ok: false, errors };
}
