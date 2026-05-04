import { useEffect, useReducer, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { Button, PageHeader } from "@/components/common";
import { ApiError } from "@/api/types";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { mapCampaignError } from "@/lib/campaign-errors";
import { useAuthStore } from "@/stores/auth";
import { useProjectStore } from "@/stores/project";
import {
  useCampaignDetailQuery,
  useCreateCampaignMutation,
  useStartCampaignMutation,
  useUpdateCampaignMutation,
} from "@/hooks/queries/campaign";
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

  const { data: editTarget } = useCampaignDetailQuery(
    projectId,
    editingCampaignId,
    mode === "edit" && projectId != null && editingCampaignId != null,
  );

  // edit 모드 hydrate
  useEffect(() => {
    if (mode !== "edit" || !editTarget) return;
    let schedule: ScheduleInput;
    if (editTarget.schedule.type === "immediate") {
      schedule = { type: "immediate" };
    } else if (editTarget.schedule.type === "scheduled") {
      schedule = { type: "scheduled", scheduledAt: editTarget.schedule.scheduledAt };
    } else {
      schedule = {
        type: "recurring",
        repeatType: editTarget.schedule.repeatType,
        repeatTime: editTarget.schedule.repeatTime,
        repeatDayOfWeek: editTarget.schedule.repeatDayOfWeek,
        repeatDayOfMonth: editTarget.schedule.repeatDayOfMonth,
        recurringCount: editTarget.schedule.recurringCount,
      };
    }
    const draft: WizardDraft = {
      ...initialDraft,
      campaignName: editTarget.campaignName,
      campaignDescription: editTarget.campaignDescription,
      campaignType: editTarget.campaignType,
      channel: editTarget.channel,
      segmentId: editTarget.segmentId,
      segmentName: editTarget.segmentName,
      schedule,
      messageTemplateId: editTarget.messageTemplateId,
      messageTemplateName: editTarget.messageTemplateName,
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

  const buildSchedulePayload = (): CreateCampaignRequest["schedule"] => {
    const s = state.draft.schedule;
    if (s.type === "scheduled") {
      // 사용자 입력은 datetime-local. UTC ISO 로 직렬화.
      const iso = s.scheduledAt && !s.scheduledAt.includes("Z")
        ? toUtcIso(s.scheduledAt)
        : s.scheduledAt;
      return { type: "scheduled", scheduledAt: iso };
    }
    if (s.type === "recurring") {
      // mock 의 ScheduleRecurring 은 nextRunAt 필수 — 없으면 빈 문자열로 (mock 측에서 +1h 로 채움).
      return {
        type: "recurring",
        repeatType: s.repeatType,
        repeatTime: s.repeatTime,
        repeatDayOfWeek: s.repeatDayOfWeek,
        repeatDayOfMonth: s.repeatDayOfMonth,
        recurringCount: s.recurringCount,
        nextRunAt: "",
      };
    }
    return { type: "immediate" };
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
      const req: CreateCampaignRequest = {
        projectId,
        campaignName: state.draft.campaignName.trim(),
        campaignDescription: state.draft.campaignDescription.trim(),
        campaignType: state.draft.campaignType,
        channel: state.draft.channel,
        segmentId: state.draft.segmentId,
        messageTemplateId: state.draft.messageTemplateId as number,
        schedule: buildSchedulePayload(),
        isTempSave: !startAfter,
        meta: {
          messageTemplateName: state.draft.messageTemplateName ?? undefined,
          segmentName: state.draft.segmentName,
          createdBy: member?.email,
        },
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
            onSelect={(id, name) =>
              dispatch({ type: "set/segment", segmentId: id, segmentName: name })
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
                  <Button
                    variant="primaryStrong"
                    size="md"
                    onClick={() => finalizeCreate(true)}
                    disabled={submitting}
                    style={{}}
                  >
                    {submitting ? "처리 중..." : startAfterLabel}
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

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
