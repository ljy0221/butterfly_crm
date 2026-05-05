import type {
  CampaignType,
  Channel,
  RepeatType,
  ScheduleInput,
} from "@/schemas/campaign";

/**
 * Sprint 6 — Campaign Wizard 상태 관리.
 *
 * 6단계 stepper 의 step state + draft data + per-step 검증 결과를
 * useReducer 로 통합 관리. Sprint 5 SegmentBuilder 의 treeReducer 패턴 참고.
 */

export type WizardStep = 1 | 2 | 3 | 4 | 5 | 6;

export interface WizardDraft {
  // Step 1
  campaignName: string;
  campaignDescription: string;
  campaignType: CampaignType;
  // Step 2
  channel: Channel;
  // Step 3
  segmentId: number | null; // null = 전체 사용자 발송
  segmentName: string | null;
  // Step 4
  schedule: ScheduleInput;
  // Step 5
  messageTemplateId: number | null;
  messageTemplateName: string | null;
  /** mode="edit" 으로 들어왔을 때 dirty 여부 추적용 (저장 활성화 판단). */
  dirty: boolean;
}

export interface FieldErrorMap {
  [field: string]: string | undefined;
}

export interface WizardState {
  step: WizardStep;
  draft: WizardDraft;
  /** step 별 마지막 검증 결과. 해당 step 활성화 시 표시. */
  errors: FieldErrorMap;
  /** 위저드 mode. "create" 면 새 캠페인, "edit" 이면 DRAFT 수정. */
  mode: "create" | "edit";
  /** edit 모드일 때만 의미 있음. */
  campaignId: number | null;
}

export type WizardAction =
  | { type: "set/step"; step: WizardStep }
  | { type: "next" }
  | { type: "prev" }
  | { type: "set/name"; value: string }
  | { type: "set/description"; value: string }
  | { type: "set/type"; value: CampaignType }
  | { type: "set/channel"; value: Channel }
  | {
      type: "set/segment";
      segmentId: number | null;
      segmentName: string | null;
    }
  | { type: "set/scheduleType"; value: ScheduleInput["type"] }
  | { type: "set/scheduledAt"; value: string }
  | { type: "set/repeatType"; value: RepeatType }
  | { type: "set/repeatTime"; value: string }
  | { type: "set/repeatDayOfWeek"; value: string }
  | { type: "set/repeatDayOfMonth"; value: number | undefined }
  | { type: "set/recurringCount"; value: number }
  | {
      type: "set/template";
      messageTemplateId: number | null;
      messageTemplateName: string | null;
    }
  | { type: "set/errors"; errors: FieldErrorMap }
  | { type: "clear/errors" }
  | { type: "hydrate"; draft: WizardDraft; campaignId: number };

export const initialDraft: WizardDraft = {
  campaignName: "",
  campaignDescription: "",
  campaignType: "ONE_TIME",
  channel: "EMAIL",
  segmentId: null,
  segmentName: null,
  schedule: { type: "immediate" },
  messageTemplateId: null,
  messageTemplateName: null,
  dirty: false,
};

export function makeInitialState(
  mode: "create" | "edit",
  campaignId: number | null = null,
): WizardState {
  return {
    step: 1,
    draft: { ...initialDraft },
    errors: {},
    mode,
    campaignId,
  };
}

function clampStep(n: number): WizardStep {
  if (n < 1) return 1;
  if (n > 6) return 6;
  return n as WizardStep;
}

function markDirty(state: WizardState, draft: WizardDraft): WizardDraft {
  return { ...draft, dirty: state.mode === "edit" ? true : draft.dirty };
}

export function wizardReducer(
  state: WizardState,
  action: WizardAction,
): WizardState {
  switch (action.type) {
    case "set/step":
      return { ...state, step: clampStep(action.step), errors: {} };
    case "next":
      return { ...state, step: clampStep(state.step + 1), errors: {} };
    case "prev":
      return { ...state, step: clampStep(state.step - 1), errors: {} };

    case "set/name": {
      const draft = markDirty(state, { ...state.draft, campaignName: action.value });
      return { ...state, draft };
    }
    case "set/description": {
      const draft = markDirty(state, {
        ...state.draft,
        campaignDescription: action.value,
      });
      return { ...state, draft };
    }
    case "set/type": {
      const draft = markDirty(state, { ...state.draft, campaignType: action.value });
      return { ...state, draft };
    }
    case "set/channel": {
      const draft = markDirty(state, { ...state.draft, channel: action.value });
      return { ...state, draft };
    }
    case "set/segment": {
      const draft = markDirty(state, {
        ...state.draft,
        segmentId: action.segmentId,
        segmentName: action.segmentName,
      });
      return { ...state, draft };
    }
    case "set/scheduleType": {
      let next: ScheduleInput;
      if (action.value === "immediate") next = { type: "immediate" };
      else if (action.value === "scheduled")
        next = { type: "scheduled", scheduledAt: "" };
      else
        next = {
          type: "recurring",
          repeatType: "DAILY",
          repeatTime: "09:00",
          recurringCount: 1,
        };
      const draft = markDirty(state, { ...state.draft, schedule: next });
      return { ...state, draft };
    }
    case "set/scheduledAt": {
      if (state.draft.schedule.type !== "scheduled") return state;
      const next: ScheduleInput = {
        type: "scheduled",
        scheduledAt: action.value,
      };
      const draft = markDirty(state, { ...state.draft, schedule: next });
      return { ...state, draft };
    }
    case "set/repeatType": {
      if (state.draft.schedule.type !== "recurring") return state;
      const next: ScheduleInput = {
        ...state.draft.schedule,
        repeatType: action.value,
      };
      const draft = markDirty(state, { ...state.draft, schedule: next });
      return { ...state, draft };
    }
    case "set/repeatTime": {
      if (state.draft.schedule.type !== "recurring") return state;
      const next: ScheduleInput = {
        ...state.draft.schedule,
        repeatTime: action.value,
      };
      const draft = markDirty(state, { ...state.draft, schedule: next });
      return { ...state, draft };
    }
    case "set/repeatDayOfWeek": {
      if (state.draft.schedule.type !== "recurring") return state;
      const next: ScheduleInput = {
        ...state.draft.schedule,
        repeatDayOfWeek: action.value,
      };
      const draft = markDirty(state, { ...state.draft, schedule: next });
      return { ...state, draft };
    }
    case "set/repeatDayOfMonth": {
      if (state.draft.schedule.type !== "recurring") return state;
      const next: ScheduleInput = {
        ...state.draft.schedule,
        repeatDayOfMonth: action.value,
      };
      const draft = markDirty(state, { ...state.draft, schedule: next });
      return { ...state, draft };
    }
    case "set/recurringCount": {
      if (state.draft.schedule.type !== "recurring") return state;
      const next: ScheduleInput = {
        ...state.draft.schedule,
        recurringCount: action.value,
      };
      const draft = markDirty(state, { ...state.draft, schedule: next });
      return { ...state, draft };
    }
    case "set/template": {
      const draft = markDirty(state, {
        ...state.draft,
        messageTemplateId: action.messageTemplateId,
        messageTemplateName: action.messageTemplateName,
      });
      return { ...state, draft };
    }
    case "set/errors":
      return { ...state, errors: action.errors };
    case "clear/errors":
      return { ...state, errors: {} };
    case "hydrate":
      return {
        ...state,
        draft: action.draft,
        campaignId: action.campaignId,
        errors: {},
      };
    default:
      return state;
  }
}
