import { delay } from "./index";
import { buildCronPreview } from "@/lib/cron-preview";
import type { CampaignStatsResponse } from "@/schemas/analytics";
import type {
  CampaignStatus,
  CampaignType,
  Channel,
  ExecutionStatus,
  RepeatType,
  SendMode,
} from "@/schemas/campaign";

/**
 * Sprint 6 — Campaign 도메인 mock store + 7개 핸들러.
 *
 * 백엔드 (com.b101.butterfly.campaign.campaign.*) 도메인 모델은 완성되어 있지만
 * Application/Presentation Layer (Service/Controller/DTO) 가 미구현이라
 * REST API 가 존재하지 않는다. 따라서 모든 호출은 ifMock("campaign", ...) 으로 mock store 사용.
 *
 * Map<campaignId, CampaignRecord> + Map<campaignId, CampaignExecution[]>.
 * 새로고침 시 휘발 (localStorage 보관은 P2).
 *
 * 검증 룰은 Campaign 도메인 factory + RecurringCronGenerator + CampaignErrorCode 와 동등.
 */

// ---------- 도메인 record ----------

export interface ScheduleImmediate {
  type: "immediate";
}
export interface ScheduleScheduled {
  type: "scheduled";
  scheduledAt: string; // UTC ISO
}
export interface ScheduleRecurring {
  type: "recurring";
  repeatType: RepeatType;
  repeatTime: string; // "HH:mm" or "HH:mm:ss"
  repeatDayOfWeek?: string; // "MON,WED"
  repeatDayOfMonth?: number;
  recurringCount: number;
  nextRunAt: string; // UTC ISO
}
export type ScheduleRecord =
  | ScheduleImmediate
  | ScheduleScheduled
  | ScheduleRecurring;

export interface CampaignRecord {
  campaignId: number;
  projectId: number;
  campaignName: string;
  campaignDescription: string;
  campaignType: CampaignType;
  channel: Channel;
  campaignStatus: CampaignStatus;
  segmentId: number | null;
  messageTemplateId: number;
  sendMode: SendMode | null; // AUTOMATION 일 때 null
  schedule: ScheduleRecord;
  recurringCron: string | null;
  recurringExecutionCount: number;
  /** 보조: 표시용 메타. */
  messageTemplateName: string;
  segmentName: string | null;
  createdBy: string; // email
  createdAt: string;
  updatedAt: string;
  version: number;
  /** 발송 통계 (mock-only). */
  sendCount: number;
  openRate: number; // 0~100
}

export interface CampaignExecutionRecord {
  campaignExecutionId: number;
  campaignId: number;
  runRound: number;
  campaignExecutionStatus: ExecutionStatus;
  sendStartedAt: string | null;
  sendCompletedAt: string | null;
  targetCount: number;
  sentCount: number;
  failedCount: number;
  failReason: string | null;
}

// ---------- 싱글톤 store ----------

const campaigns = new Map<number, CampaignRecord>();
const executionsByCampaign = new Map<number, CampaignExecutionRecord[]>();
let nextCampaignId = 1;
let nextExecutionId = 1;
let seeded = false;

function nowMinus(daysAgo: number, hoursAgo = 0): string {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  d.setHours(d.getHours() - hoursAgo);
  return d.toISOString();
}

function nowPlus(daysAhead: number, hoursAhead = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  d.setHours(d.getHours() + hoursAhead);
  return d.toISOString();
}

function seed(): void {
  if (seeded) return;
  seeded = true;

  const seeds: Array<Omit<CampaignRecord, "campaignId" | "version">> = [
    {
      projectId: 1,
      campaignName: "Summer Sale 2026",
      campaignDescription: "여름 시즌 프로모션 캠페인",
      campaignType: "ONE_TIME",
      channel: "EMAIL",
      campaignStatus: "ACTIVE",
      segmentId: 1,
      messageTemplateId: 3,
      sendMode: "IMMEDIATE",
      schedule: { type: "immediate" },
      recurringCron: null,
      recurringExecutionCount: 0,
      messageTemplateName: "주문 완료 안내",
      segmentName: "최근 30일 활성 고객",
      createdBy: "marketer@butterfly.io",
      createdAt: nowMinus(2),
      updatedAt: nowMinus(2),
      sendCount: 15000,
      openRate: 32.5,
    },
    {
      projectId: 1,
      campaignName: "VIP 알림톡",
      campaignDescription: "VIP 고객 대상 카카오 알림톡",
      campaignType: "ONE_TIME",
      channel: "KAKAO",
      campaignStatus: "ACTIVE",
      segmentId: 2,
      messageTemplateId: 5,
      sendMode: "SCHEDULED",
      schedule: { type: "scheduled", scheduledAt: nowPlus(1, 6) },
      recurringCron: null,
      recurringExecutionCount: 0,
      messageTemplateName: "프로모션 알림톡",
      segmentName: "VIP 고객",
      createdBy: "marketer@butterfly.io",
      createdAt: nowMinus(3, 4),
      updatedAt: nowMinus(1),
      sendCount: 1284,
      openRate: 64.2,
    },
    {
      projectId: 1,
      campaignName: "주간 뉴스레터",
      campaignDescription: "매주 월요일 발송되는 뉴스레터",
      campaignType: "ONE_TIME",
      channel: "EMAIL",
      campaignStatus: "ACTIVE",
      segmentId: null,
      messageTemplateId: 3,
      sendMode: "RECURRING",
      schedule: {
        type: "recurring",
        repeatType: "WEEKLY",
        repeatTime: "09:00",
        repeatDayOfWeek: "MON",
        recurringCount: 12,
        nextRunAt: nowPlus(2, 0),
      },
      recurringCron: "0 0 9 * * MON",
      recurringExecutionCount: 4,
      messageTemplateName: "주문 완료 안내",
      segmentName: null,
      createdBy: "ceo@butterfly.io",
      createdAt: nowMinus(28),
      updatedAt: nowMinus(7),
      sendCount: 84320,
      openRate: 41.8,
    },
    {
      projectId: 1,
      campaignName: "장바구니 리마인더 (초안)",
      campaignDescription: "24시간 이상 미결제 장바구니 보유 고객 푸시",
      campaignType: "ONE_TIME",
      channel: "PUSH",
      campaignStatus: "DRAFT",
      segmentId: 3,
      messageTemplateId: 2,
      sendMode: "IMMEDIATE",
      schedule: { type: "immediate" },
      recurringCron: null,
      recurringExecutionCount: 0,
      messageTemplateName: "장바구니 리마인더",
      segmentName: "장바구니 보유 고객",
      createdBy: "marketer@butterfly.io",
      createdAt: nowMinus(0, 4),
      updatedAt: nowMinus(0, 2),
      sendCount: 0,
      openRate: 0,
    },
    {
      projectId: 1,
      campaignName: "신규 가입 환영 SMS",
      campaignDescription: "회원가입 직후 발송되는 환영 메시지",
      campaignType: "ONE_TIME",
      channel: "SMS",
      campaignStatus: "DRAFT",
      segmentId: null,
      messageTemplateId: 1,
      sendMode: "SCHEDULED",
      schedule: { type: "scheduled", scheduledAt: nowPlus(3, 9) },
      recurringCron: null,
      recurringExecutionCount: 0,
      messageTemplateName: "환영 메시지",
      segmentName: null,
      createdBy: "ceo@butterfly.io",
      createdAt: nowMinus(1, 6),
      updatedAt: nowMinus(0, 3),
      sendCount: 0,
      openRate: 0,
    },
    {
      projectId: 1,
      campaignName: "이탈 방지 윈백",
      campaignDescription: "30일 미접속 고객 윈백 캠페인",
      campaignType: "ONE_TIME",
      channel: "PUSH",
      campaignStatus: "PAUSED",
      segmentId: 4,
      messageTemplateId: 6,
      sendMode: "RECURRING",
      schedule: {
        type: "recurring",
        repeatType: "MONTHLY",
        repeatTime: "10:30",
        repeatDayOfMonth: 1,
        recurringCount: 6,
        nextRunAt: nowPlus(15, 0),
      },
      recurringCron: "0 30 10 1 * *",
      recurringExecutionCount: 2,
      messageTemplateName: "이탈 방지 캠페인",
      segmentName: "30일 미접속 고객",
      createdBy: "marketer@butterfly.io",
      createdAt: nowMinus(45),
      updatedAt: nowMinus(5),
      sendCount: 18420,
      openRate: 12.4,
    },
    {
      projectId: 1,
      campaignName: "Q1 성과 보고",
      campaignDescription: "1분기 종료 후 발송된 성과 요약",
      campaignType: "ONE_TIME",
      channel: "EMAIL",
      campaignStatus: "ENDED",
      segmentId: null,
      messageTemplateId: 3,
      sendMode: "IMMEDIATE",
      schedule: { type: "immediate" },
      recurringCron: null,
      recurringExecutionCount: 0,
      messageTemplateName: "주문 완료 안내",
      segmentName: null,
      createdBy: "ceo@butterfly.io",
      createdAt: nowMinus(60),
      updatedAt: nowMinus(60),
      sendCount: 9020,
      openRate: 38.9,
    },
    {
      projectId: 1,
      campaignName: "재고 부족 webhook",
      campaignDescription: "운영팀 Slack webhook 으로 재고 부족 알림",
      campaignType: "ONE_TIME",
      channel: "WEBHOOK",
      campaignStatus: "ENDED",
      segmentId: null,
      messageTemplateId: 4,
      sendMode: "RECURRING",
      schedule: {
        type: "recurring",
        repeatType: "DAILY",
        repeatTime: "08:00",
        recurringCount: 30,
        nextRunAt: nowMinus(1),
      },
      recurringCron: "0 0 8 * * *",
      recurringExecutionCount: 30,
      messageTemplateName: "재고 알림",
      segmentName: null,
      createdBy: "ops@butterfly.io",
      createdAt: nowMinus(40),
      updatedAt: nowMinus(1),
      sendCount: 30,
      openRate: 0,
    },
  ];

  for (const s of seeds) {
    const id = nextCampaignId++;
    campaigns.set(id, { ...s, campaignId: id, version: 0 });
    // ENDED 캠페인은 실행 이력 1개 SUCCESS 시드
    if (s.campaignStatus === "ENDED") {
      const eid = nextExecutionId++;
      executionsByCampaign.set(id, [
        {
          campaignExecutionId: eid,
          campaignId: id,
          runRound: 1,
          campaignExecutionStatus: "SUCCESS",
          sendStartedAt: nowMinus(60, 1),
          sendCompletedAt: nowMinus(60, 0),
          targetCount: s.sendCount,
          sentCount: s.sendCount,
          failedCount: 0,
          failReason: null,
        },
      ]);
    } else if (s.campaignStatus === "ACTIVE" || s.campaignStatus === "PAUSED") {
      // 진행 중 캠페인은 1회차 SEND 또는 SUCCESS 이력 시드
      const eid = nextExecutionId++;
      executionsByCampaign.set(id, [
        {
          campaignExecutionId: eid,
          campaignId: id,
          runRound: 1,
          campaignExecutionStatus: "SUCCESS",
          sendStartedAt: nowMinus(2, 1),
          sendCompletedAt: nowMinus(2, 0),
          targetCount: Math.max(1, Math.floor(s.sendCount / 2)),
          sentCount: Math.max(1, Math.floor(s.sendCount / 2)),
          failedCount: 0,
          failReason: null,
        },
      ]);
    }
  }
}

// ---------- 에러 helper ----------

function fail(code: string, message?: string, status = 400): never {
  throw Object.assign(new Error(message ?? code), { code, status });
}

// ---------- DTO 타입 ----------

export interface CampaignListItemMock {
  campaignId: number;
  projectId: number;
  campaignName: string;
  campaignType: CampaignType;
  channel: Channel;
  campaignStatus: CampaignStatus;
  sendMode: SendMode | null;
  segmentId: number | null;
  segmentName: string | null;
  messageTemplateId: number;
  messageTemplateName: string;
  sendCount: number;
  openRate: number;
  createdAt: string;
  createdBy: string;
  /** SCHEDULED 의 scheduledAt 또는 RECURRING 의 nextRunAt. */
  nextRunAt: string | null;
}

export interface CampaignListMockParams {
  projectId: number;
  status?: CampaignStatus;
  channel?: Channel;
  q?: string;
  page?: number;
  size?: number;
  sort?: "createdAt,desc" | "createdAt,asc" | "name,asc";
}

export interface CampaignListMockResponse {
  campaigns: CampaignListItemMock[];
  pagination: {
    currentPage: number;
    totalPages: number;
    totalElements: number;
    pageSize: number;
  };
}

export interface CampaignDetailMock extends CampaignRecord {
  executions: CampaignExecutionRecord[];
}

export interface CreateCampaignMockRequest {
  projectId: number;
  campaignName: string;
  campaignDescription: string;
  campaignType: CampaignType;
  channel: Channel;
  segmentId: number | null;
  messageTemplateId: number;
  schedule: ScheduleRecord;
  /** mock-only — UI 표시용 메타데이터 봉합. */
  meta?: {
    messageTemplateName?: string;
    segmentName?: string | null;
    createdBy?: string;
  };
  isTempSave: boolean;
}

export type UpdateCampaignMockRequest = CreateCampaignMockRequest;

// ---------- record ↔ list item 변환 ----------

function recordToListItem(r: CampaignRecord): CampaignListItemMock {
  let nextRunAt: string | null = null;
  if (r.schedule.type === "scheduled") nextRunAt = r.schedule.scheduledAt;
  else if (r.schedule.type === "recurring") nextRunAt = r.schedule.nextRunAt;
  return {
    campaignId: r.campaignId,
    projectId: r.projectId,
    campaignName: r.campaignName,
    campaignType: r.campaignType,
    channel: r.channel,
    campaignStatus: r.campaignStatus,
    sendMode: r.sendMode,
    segmentId: r.segmentId,
    segmentName: r.segmentName,
    messageTemplateId: r.messageTemplateId,
    messageTemplateName: r.messageTemplateName,
    sendCount: r.sendCount,
    openRate: r.openRate,
    createdAt: r.createdAt,
    createdBy: r.createdBy,
    nextRunAt,
  };
}

// ---------- 검증 ----------

function validateCommon(req: CreateCampaignMockRequest): void {
  if (!req.projectId) fail("CAMPAIGN_001", "프로젝트 정보는 필수입니다");
  if (!req.messageTemplateId) fail("CAMPAIGN_002", "메시지 템플릿은 필수입니다");
  if (!req.campaignName || req.campaignName.trim() === "")
    fail("CAMPAIGN_004", "캠페인 이름은 필수입니다");
  if (!req.campaignDescription || req.campaignDescription.trim() === "")
    fail("CAMPAIGN_005", "캠페인 설명은 필수입니다");
}

function validateSchedule(s: ScheduleRecord): void {
  if (s.type === "scheduled") {
    if (!s.scheduledAt) fail("CAMPAIGN_007", "예약 발송 시각은 필수입니다");
    const dt = new Date(s.scheduledAt);
    if (Number.isNaN(dt.getTime()) || dt.getTime() <= Date.now())
      fail("CAMPAIGN_008", "발송 예정 시각은 현재보다 과거일 수 없습니다");
  } else if (s.type === "recurring") {
    if (!s.repeatType) fail("CAMPAIGN_009", "반복 유형은 필수입니다");
    if (!s.recurringCount || s.recurringCount < 1)
      fail("CAMPAIGN_010", "목표 반복 발송 횟수는 1 이상이어야 합니다");
    if (!s.repeatTime) fail("CAMPAIGN_011", "반복 발송 시각은 필수입니다");
    if (s.repeatType === "WEEKLY" && (!s.repeatDayOfWeek || s.repeatDayOfWeek.trim() === ""))
      fail("CAMPAIGN_012", "주간 반복 시 요일은 필수입니다");
    if (s.repeatType === "MONTHLY") {
      if (s.repeatDayOfMonth == null || s.repeatDayOfMonth < 1 || s.repeatDayOfMonth > 31)
        fail("CAMPAIGN_014", "월간 반복 일자는 1~31 사이여야 합니다");
    }
    // WEEKLY 요일 토큰 화이트리스트 검증
    if (s.repeatType === "WEEKLY" && s.repeatDayOfWeek) {
      const ALLOWED = new Set(["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"]);
      for (const t of s.repeatDayOfWeek.split(",").map((x) => x.trim().toUpperCase())) {
        if (!ALLOWED.has(t))
          fail("CAMPAIGN_013", "요일 값은 MON~SUN 만 사용 가능합니다");
      }
    }
  }
}

// ---------- 7개 핸들러 ----------

export async function listCampaignsMock(
  params: CampaignListMockParams,
): Promise<CampaignListMockResponse> {
  seed();
  let all = Array.from(campaigns.values()).filter(
    (c) => c.projectId === params.projectId,
  );
  if (params.status) all = all.filter((c) => c.campaignStatus === params.status);
  if (params.channel) all = all.filter((c) => c.channel === params.channel);
  if (params.q && params.q.trim()) {
    const q = params.q.trim().toLowerCase();
    all = all.filter(
      (c) =>
        c.campaignName.toLowerCase().includes(q) ||
        c.campaignDescription.toLowerCase().includes(q),
    );
  }
  const sort = params.sort ?? "createdAt,desc";
  all.sort((a, b) => {
    if (sort === "name,asc") return a.campaignName.localeCompare(b.campaignName);
    if (sort === "createdAt,asc") return a.createdAt.localeCompare(b.createdAt);
    return b.createdAt.localeCompare(a.createdAt);
  });
  const size = params.size ?? 20;
  const page = params.page ?? 0;
  const start = page * size;
  const slice = all.slice(start, start + size);
  return delay(
    {
      campaigns: slice.map(recordToListItem),
      pagination: {
        currentPage: page,
        totalPages: Math.max(1, Math.ceil(all.length / size)),
        totalElements: all.length,
        pageSize: size,
      },
    },
    220,
  );
}

export async function getCampaignMock(campaignId: number): Promise<CampaignDetailMock> {
  seed();
  const r = campaigns.get(campaignId);
  if (!r) fail("CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없습니다", 404);
  const exes = executionsByCampaign.get(campaignId) ?? [];
  return delay({ ...(r as CampaignRecord), executions: [...exes] }, 200);
}

export async function createCampaignMock(
  req: CreateCampaignMockRequest,
): Promise<CampaignDetailMock> {
  seed();
  validateCommon(req);
  validateSchedule(req.schedule);
  // segmentId 가 null 이 아니면 mock segment 존재 여부는 굳이 검증하지 않는다 (실제 DB는 백엔드).

  const id = nextCampaignId++;
  let recurringCron: string | null = null;
  let sendMode: SendMode | null = null;
  if (req.schedule.type === "immediate") sendMode = "IMMEDIATE";
  else if (req.schedule.type === "scheduled") sendMode = "SCHEDULED";
  else if (req.schedule.type === "recurring") {
    sendMode = "RECURRING";
    const cron = buildCronPreview({
      repeatType: req.schedule.repeatType,
      repeatTime: req.schedule.repeatTime,
      repeatDayOfWeek: req.schedule.repeatDayOfWeek,
      repeatDayOfMonth: req.schedule.repeatDayOfMonth,
    });
    recurringCron = cron.ok ? cron.cron ?? null : null;
  }
  // AUTOMATION 은 sendMode 가 null
  if (req.campaignType === "AUTOMATION") sendMode = null;

  const now = new Date().toISOString();
  // recurring 의 경우 nextRunAt 미지정이면 현재 + 1h 로 채움
  let scheduleSaved: ScheduleRecord = req.schedule;
  if (
    scheduleSaved.type === "recurring" &&
    (!scheduleSaved.nextRunAt || scheduleSaved.nextRunAt === "")
  ) {
    scheduleSaved = {
      ...scheduleSaved,
      nextRunAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    };
  }

  const record: CampaignRecord = {
    campaignId: id,
    projectId: req.projectId,
    campaignName: req.campaignName.trim(),
    campaignDescription: req.campaignDescription.trim(),
    campaignType: req.campaignType,
    channel: req.channel,
    campaignStatus: "DRAFT",
    segmentId: req.segmentId,
    messageTemplateId: req.messageTemplateId,
    sendMode,
    schedule: scheduleSaved,
    recurringCron,
    recurringExecutionCount: 0,
    messageTemplateName: req.meta?.messageTemplateName ?? `템플릿 #${req.messageTemplateId}`,
    segmentName:
      req.meta?.segmentName !== undefined
        ? req.meta.segmentName
        : req.segmentId == null
          ? null
          : `세그먼트 #${req.segmentId}`,
    createdBy: req.meta?.createdBy ?? "unknown@butterfly.io",
    createdAt: now,
    updatedAt: now,
    version: 0,
    sendCount: 0,
    openRate: 0,
  };
  campaigns.set(id, record);
  executionsByCampaign.set(id, []);
  return delay({ ...record, executions: [] }, 220);
}

export async function updateCampaignMock(
  campaignId: number,
  req: UpdateCampaignMockRequest,
): Promise<CampaignDetailMock> {
  seed();
  const existing = campaigns.get(campaignId);
  if (!existing) fail("CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없습니다", 404);
  if ((existing as CampaignRecord).campaignStatus !== "DRAFT")
    fail("CAMPAIGN_ALREADY_ACTIVE", "DRAFT 상태의 캠페인만 수정할 수 있습니다", 409);

  validateCommon(req);
  validateSchedule(req.schedule);

  const e = existing as CampaignRecord;
  let recurringCron: string | null = null;
  let sendMode: SendMode | null = null;
  if (req.schedule.type === "immediate") sendMode = "IMMEDIATE";
  else if (req.schedule.type === "scheduled") sendMode = "SCHEDULED";
  else if (req.schedule.type === "recurring") {
    sendMode = "RECURRING";
    const cron = buildCronPreview({
      repeatType: req.schedule.repeatType,
      repeatTime: req.schedule.repeatTime,
      repeatDayOfWeek: req.schedule.repeatDayOfWeek,
      repeatDayOfMonth: req.schedule.repeatDayOfMonth,
    });
    recurringCron = cron.ok ? cron.cron ?? null : null;
  }
  if (req.campaignType === "AUTOMATION") sendMode = null;

  let scheduleSaved: ScheduleRecord = req.schedule;
  if (
    scheduleSaved.type === "recurring" &&
    (!scheduleSaved.nextRunAt || scheduleSaved.nextRunAt === "")
  ) {
    scheduleSaved = {
      ...scheduleSaved,
      nextRunAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    };
  }

  const updated: CampaignRecord = {
    ...e,
    campaignName: req.campaignName.trim(),
    campaignDescription: req.campaignDescription.trim(),
    campaignType: req.campaignType,
    channel: req.channel,
    segmentId: req.segmentId,
    messageTemplateId: req.messageTemplateId,
    sendMode,
    schedule: scheduleSaved,
    recurringCron,
    messageTemplateName: req.meta?.messageTemplateName ?? e.messageTemplateName,
    segmentName:
      req.meta?.segmentName !== undefined ? req.meta.segmentName : e.segmentName,
    updatedAt: new Date().toISOString(),
    version: e.version + 1,
  };
  campaigns.set(campaignId, updated);
  return delay(
    { ...updated, executions: [...(executionsByCampaign.get(campaignId) ?? [])] },
    200,
  );
}

export async function deleteCampaignMock(
  campaignId: number,
): Promise<{ campaignId: number }> {
  seed();
  const existing = campaigns.get(campaignId);
  if (!existing) fail("CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없습니다", 404);
  if ((existing as CampaignRecord).campaignStatus !== "DRAFT")
    fail(
      "CAMPAIGN_ALREADY_ACTIVE",
      "DRAFT 상태의 캠페인만 삭제할 수 있습니다",
      409,
    );
  campaigns.delete(campaignId);
  executionsByCampaign.delete(campaignId);
  return delay({ campaignId }, 180);
}

export async function startCampaignMock(
  campaignId: number,
): Promise<CampaignDetailMock> {
  seed();
  const existing = campaigns.get(campaignId);
  if (!existing) fail("CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없습니다", 404);

  const e = existing as CampaignRecord;
  if (e.campaignStatus === "ACTIVE")
    fail("CAMPAIGN_ALREADY_ACTIVE", "이미 시작된 캠페인입니다", 409);
  if (e.campaignStatus === "ENDED")
    fail("CAMPAIGN_ALREADY_ACTIVE", "종료된 캠페인은 시작할 수 없습니다", 409);

  // DRAFT 또는 PAUSED → ACTIVE 전이
  const updated: CampaignRecord = {
    ...e,
    campaignStatus: "ACTIVE",
    updatedAt: new Date().toISOString(),
    version: e.version + 1,
  };
  campaigns.set(campaignId, updated);

  // Execution 시뮬레이션: READY → 0.5s SEND → 1.5s SUCCESS
  const exes = executionsByCampaign.get(campaignId) ?? [];
  const runRound = exes.length + 1;
  const eid = nextExecutionId++;
  const targetCount = e.segmentId == null ? 25000 : 1500;
  const ex: CampaignExecutionRecord = {
    campaignExecutionId: eid,
    campaignId,
    runRound,
    campaignExecutionStatus: "READY",
    sendStartedAt: null,
    sendCompletedAt: null,
    targetCount,
    sentCount: 0,
    failedCount: 0,
    failReason: null,
  };
  exes.push(ex);
  executionsByCampaign.set(campaignId, exes);

  // 0.5s 후 SEND
  setTimeout(() => {
    const list = executionsByCampaign.get(campaignId) ?? [];
    const idx = list.findIndex((x) => x.campaignExecutionId === eid);
    if (idx >= 0) {
      list[idx] = {
        ...list[idx],
        campaignExecutionStatus: "SEND",
        sendStartedAt: new Date().toISOString(),
      };
      executionsByCampaign.set(campaignId, [...list]);
    }
  }, 500);
  // 1.5s 후 SUCCESS
  setTimeout(() => {
    const list = executionsByCampaign.get(campaignId) ?? [];
    const idx = list.findIndex((x) => x.campaignExecutionId === eid);
    if (idx >= 0) {
      list[idx] = {
        ...list[idx],
        campaignExecutionStatus: "SUCCESS",
        sentCount: targetCount,
        sendCompletedAt: new Date().toISOString(),
      };
      executionsByCampaign.set(campaignId, [...list]);
    }
    // 캠페인 통계 갱신 (mock)
    const c = campaigns.get(campaignId);
    if (c) {
      campaigns.set(campaignId, {
        ...c,
        sendCount: c.sendCount + targetCount,
        recurringExecutionCount:
          c.sendMode === "RECURRING"
            ? c.recurringExecutionCount + 1
            : c.recurringExecutionCount,
      });
    }
  }, 1500);

  return delay({ ...updated, executions: [...exes] }, 180);
}

export async function pauseCampaignMock(
  campaignId: number,
): Promise<CampaignDetailMock> {
  seed();
  const existing = campaigns.get(campaignId);
  if (!existing) fail("CAMPAIGN_NOT_FOUND", "캠페인을 찾을 수 없습니다", 404);
  const e = existing as CampaignRecord;
  if (e.campaignStatus !== "ACTIVE")
    fail(
      "CAMPAIGN_NOT_ACTIVE",
      "ACTIVE 상태의 캠페인만 일시정지할 수 있습니다",
      409,
    );
  const updated: CampaignRecord = {
    ...e,
    campaignStatus: "PAUSED",
    updatedAt: new Date().toISOString(),
    version: e.version + 1,
  };
  campaigns.set(campaignId, updated);
  return delay(
    { ...updated, executions: [...(executionsByCampaign.get(campaignId) ?? [])] },
    180,
  );
}

// ---------- Sprint 5 segment 연동 export ----------

/**
 * 특정 segmentId 와 연결된 캠페인 목록을 4-tab 으로 그룹화하여 반환.
 * Sprint 5 _mock/segment.ts 의 getLinkedCampaignsMock 가 이 함수를 사용한다.
 *
 * 순환 import 방지: campaign → segment 단방향. segment 측은 이 함수만 import.
 */
export function getCampaignsBySegmentId(segmentId: number): {
  active: Array<{ campaignId: number; name: string; campaignStatus: string }>;
  paused: Array<{ campaignId: number; name: string; campaignStatus: string }>;
  draft: Array<{ campaignId: number; name: string; campaignStatus: string }>;
  ended: Array<{ campaignId: number; name: string; campaignStatus: string }>;
  totalCount: number;
} {
  seed();
  const matched = Array.from(campaigns.values()).filter(
    (c) => c.segmentId === segmentId,
  );
  const toSummary = (c: CampaignRecord) => ({
    campaignId: c.campaignId,
    name: c.campaignName,
    campaignStatus: c.campaignStatus,
  });
  return {
    active: matched.filter((c) => c.campaignStatus === "ACTIVE").map(toSummary),
    paused: matched.filter((c) => c.campaignStatus === "PAUSED").map(toSummary),
    draft: matched.filter((c) => c.campaignStatus === "DRAFT").map(toSummary),
    ended: matched.filter((c) => c.campaignStatus === "ENDED").map(toSummary),
    totalCount: matched.length,
  };
}

/**
 * analytics getCampaignStats mock-fallback.
 * mock-store 의 sendCount/openRate + executions 통계로 가짜 stats 생성.
 */
/**
 * analytics getCampaignStats mock-fallback.
 *
 * ADR-0007 supersede 적용:
 *  - openCount/convertCount + 비율 4종 (delivery/open/click/convert).
 *  - clickRate 분모: open (ADR-0003 delivered → ADR-0007 open).
 *  - openRate = delivered 기준 70%, convertRate = open 기준 8% 가정 (시연용 합성).
 */
export function getCampaignStatsMock(campaignId: number): CampaignStatsResponse {
  seed();
  const c = campaigns.get(campaignId);
  if (!c) {
    return {
      campaignId,
      totalTarget: 0,
      sentCount: 0,
      deliveredCount: 0,
      openCount: 0,
      clickedCount: 0,
      convertCount: 0,
      failedCount: 0,
      deliveryRate: null,
      openRate: null,
      clickRate: null,
      convertRate: null,
      executions: [],
      metricsComputedAt: new Date().toISOString(),
    };
  }
  const exes = executionsByCampaign.get(campaignId) ?? [];
  const sentCount = c.sendCount;
  const deliveredCount = Math.floor(sentCount * 0.95);
  // mock-store openRate(0~100) → openCount = delivered * (openRate/100).
  const openCount = Math.floor(deliveredCount * (c.openRate / 100));
  // ADR-0007: clickCount는 open 기준으로 합성 — 평균 30%가 클릭.
  const clickedCount = Math.floor(openCount * 0.3);
  // 평균 8%가 전환 (sent 기준) — 시연용 합성치.
  const convertCount = Math.floor(sentCount * 0.08);
  const failedCount = sentCount - deliveredCount;
  const deliveryRate = sentCount > 0 ? deliveredCount / sentCount : null;
  const openRate = deliveredCount > 0 ? openCount / deliveredCount : null;
  // ADR-0007: 분모 = open.
  const clickRate = openCount > 0 ? clickedCount / openCount : null;
  const convertRate = sentCount > 0 ? convertCount / sentCount : null;
  return {
    campaignId,
    totalTarget: sentCount,
    sentCount,
    deliveredCount,
    openCount,
    clickedCount,
    convertCount,
    failedCount,
    deliveryRate,
    openRate,
    clickRate,
    convertRate,
    executions: exes.map((e) => {
      const exDelivered = Math.floor(e.sentCount * 0.95);
      const exOpen = Math.floor(exDelivered * (c.openRate / 100));
      const exClicked = Math.floor(exOpen * 0.3);
      const exConvert = Math.floor(e.sentCount * 0.08);
      return {
        executionId: e.campaignExecutionId,
        status: e.campaignExecutionStatus,
        sendStartedAt: e.sendStartedAt ?? c.createdAt,
        sentCount: e.sentCount,
        deliveredCount: exDelivered,
        openCount: exOpen,
        clickedCount: exClicked,
        convertCount: exConvert,
      };
    }),
    metricsComputedAt: new Date().toISOString(),
  };
}
