import { delay } from "./index";
import type { Channel, TemplateStatus } from "@/schemas/template";
import { extractVariables } from "@/schemas/template";

/**
 * Sprint 4 — Message Template mock store.
 *
 * 백엔드 GET/PUT/DELETE 미구현 시 사용하는 in-memory 어댑터.
 * 모듈 싱글톤 Map<id, TemplateRecord> 으로 보관하며, 새로고침 시 seed 재생성.
 *
 * POST(/api/v1/templates) 성공 시 외부에서 mockUpsertTemplate(...)으로
 * 응답 messageTemplateId + 요청 페이로드를 봉합 저장한다.
 * (백엔드가 description/title 같은 보조 필드를 저장하지 않으므로
 *  mock 측에서 별도로 보존해야 상세/수정 모달이 일관된 데이터를 보여줄 수 있음.)
 */

// ---------- 도메인 모델 ----------

export interface TemplateRecord {
  messageTemplateId: number;
  projectId: number;
  messageTemplateName: string;
  channel: Channel;
  messageTemplateBody: string;
  requiredVariables: string[];
  status: TemplateStatus;
  createdBy: string;
  createdAt: string; // ISO
  updatedAt: string; // ISO
  // mock-only 보조 필드 (백엔드 미저장)
  description?: string;
  title?: string;
}

export interface TemplateSummary {
  messageTemplateId: number;
  messageTemplateName: string;
  channel: Channel;
  messageTemplateBody: string;
  description?: string;
  status: TemplateStatus;
  createdBy: string;
  createdAt: string;
}

export interface TemplateListMockParams {
  projectId: number;
  page?: number;
  size?: number;
  channel?: Channel | "ALL";
  q?: string;
  sort?: "createdAt,desc" | "createdAt,asc" | "name,asc";
}

export interface TemplateListMockResponse {
  templates: TemplateSummary[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

// ---------- 싱글톤 store ----------

const store = new Map<number, TemplateRecord>();
let nextId = 1;
let seeded = false;

function nowMinus(daysAgo: number, hoursAgo = 0): string {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  d.setHours(d.getHours() - hoursAgo);
  return d.toISOString();
}

function seed(): void {
  if (seeded) return;
  seeded = true;
  const seeds: Array<Omit<TemplateRecord, "messageTemplateId" | "requiredVariables">> = [
    {
      projectId: 1,
      messageTemplateName: "환영 메시지",
      channel: "SMS",
      messageTemplateBody:
        "안녕하세요 {{name}}님! 버터플라이에 오신 것을 환영합니다. 첫 결제 시 {{coupon_code}} 쿠폰을 사용해보세요.",
      status: "DRAFT",
      createdBy: "ceo@butterfly.io",
      createdAt: nowMinus(12),
      updatedAt: nowMinus(12),
      description: "신규 가입자에게 발송되는 SMS",
    },
    {
      projectId: 1,
      messageTemplateName: "장바구니 리마인더",
      channel: "PUSH",
      messageTemplateBody:
        "{{name}}님, 담아두신 [{{product_name}}] 외 {{item_count}}개 상품이 기다리고 있어요. 오늘만 15% 추가 할인!",
      status: "READY",
      createdBy: "marketer@butterfly.io",
      createdAt: nowMinus(7, 3),
      updatedAt: nowMinus(2, 5),
      description: "24시간 이상 미결제 장바구니 보유 고객에게 발송",
      title: "장바구니에 담아두신 상품이 있어요",
    },
    {
      projectId: 1,
      messageTemplateName: "주문 완료 안내",
      channel: "EMAIL",
      messageTemplateBody:
        "{{name}}님, 주문해주셔서 감사합니다. 주문번호 {{order_id}} 의 배송이 시작되었습니다. 예상 도착일: {{eta_date}}",
      status: "READY",
      createdBy: "ceo@butterfly.io",
      createdAt: nowMinus(5),
      updatedAt: nowMinus(5),
      description: "결제 완료 직후 발송되는 트랜잭션 이메일",
      title: "주문이 정상적으로 접수되었습니다",
    },
    {
      projectId: 1,
      messageTemplateName: "재고 알림",
      channel: "WEBHOOK",
      messageTemplateBody:
        "{{product_name}} 의 재고가 임계치({{threshold}})에 도달했습니다. 현재 수량: {{stock_count}}",
      status: "DRAFT",
      createdBy: "ops@butterfly.io",
      createdAt: nowMinus(3, 2),
      updatedAt: nowMinus(3, 2),
      description: "Slack 채널 webhook 으로 재고 부족 알림",
    },
    {
      projectId: 1,
      messageTemplateName: "프로모션 알림톡",
      channel: "KAKAO",
      messageTemplateBody:
        "[{{brand_name}}] {{name}}님께만 드리는 특별한 혜택! {{discount_rate}}% 할인 쿠폰이 발급되었습니다. 유효기간: {{expires_at}}",
      status: "READY",
      createdBy: "marketer@butterfly.io",
      createdAt: nowMinus(1, 8),
      updatedAt: nowMinus(0, 4),
      description: "VIP 고객 대상 카카오 알림톡 캠페인",
      title: "VIP 고객님을 위한 혜택",
    },
    {
      projectId: 1,
      messageTemplateName: "이탈 방지 캠페인",
      channel: "PUSH",
      messageTemplateBody:
        "{{name}}님, 오랜만이에요. 마지막 방문 후 {{days_since}}일이 지났어요. 돌아오시면 {{coupon_code}} 쿠폰을 드릴게요.",
      status: "ARCHIVED",
      createdBy: "marketer@butterfly.io",
      createdAt: nowMinus(20),
      updatedAt: nowMinus(15),
      description: "30일 미접속 고객 윈백",
      title: "오랜만이에요!",
    },
  ];
  for (const s of seeds) {
    const id = nextId++;
    store.set(id, {
      ...s,
      messageTemplateId: id,
      requiredVariables: extractVariables(s.messageTemplateBody),
    });
  }
}

// ---------- 외부 API ----------

/** POST 성공 시 외부에서 호출. 응답으로 받은 messageTemplateId 와 요청 페이로드를 봉합. */
export function mockUpsertTemplate(
  messageTemplateId: number,
  params: {
    projectId: number;
    messageTemplateName: string;
    channel: Channel;
    messageTemplateBody: string;
    description?: string;
    title?: string;
    createdBy: string;
  },
): TemplateRecord {
  seed();
  const now = new Date().toISOString();
  const record: TemplateRecord = {
    messageTemplateId,
    projectId: params.projectId,
    messageTemplateName: params.messageTemplateName,
    channel: params.channel,
    messageTemplateBody: params.messageTemplateBody,
    requiredVariables: extractVariables(params.messageTemplateBody),
    status: "DRAFT",
    createdBy: params.createdBy,
    createdAt: now,
    updatedAt: now,
    description: params.description,
    title: params.title,
  };
  store.set(messageTemplateId, record);
  // store 내 시퀀스를 실 PK 위로 끌어올려 mock 자체 신규 생성과 충돌 방지
  if (messageTemplateId >= nextId) nextId = messageTemplateId + 1;
  return record;
}

export async function listTemplatesMock(
  params: TemplateListMockParams,
): Promise<TemplateListMockResponse> {
  seed();
  const all = Array.from(store.values()).filter(
    (t) => t.projectId === params.projectId,
  );

  const channelFilter = params.channel && params.channel !== "ALL"
    ? all.filter((t) => t.channel === params.channel)
    : all;

  const q = (params.q ?? "").trim().toLowerCase();
  const filtered = q
    ? channelFilter.filter(
        (t) =>
          t.messageTemplateName.toLowerCase().includes(q) ||
          t.messageTemplateBody.toLowerCase().includes(q) ||
          (t.description?.toLowerCase().includes(q) ?? false),
      )
    : channelFilter;

  const sort = params.sort ?? "createdAt,desc";
  const sorted = [...filtered].sort((a, b) => {
    if (sort === "name,asc") {
      return a.messageTemplateName.localeCompare(b.messageTemplateName);
    }
    if (sort === "createdAt,asc") {
      return a.createdAt.localeCompare(b.createdAt);
    }
    return b.createdAt.localeCompare(a.createdAt);
  });

  const size = params.size ?? 12;
  const page = params.page ?? 0;
  const start = page * size;
  const slice = sorted.slice(start, start + size);
  const totalElements = sorted.length;
  const totalPages = Math.max(1, Math.ceil(totalElements / size));

  const summaries: TemplateSummary[] = slice.map((t) => ({
    messageTemplateId: t.messageTemplateId,
    messageTemplateName: t.messageTemplateName,
    channel: t.channel,
    messageTemplateBody: t.messageTemplateBody,
    description: t.description,
    status: t.status,
    createdBy: t.createdBy,
    createdAt: t.createdAt,
  }));

  return delay(
    {
      templates: summaries,
      totalElements,
      totalPages,
      currentPage: page,
    },
    220,
  );
}

export async function getTemplateMock(id: number): Promise<TemplateRecord> {
  seed();
  const record = store.get(id);
  if (!record) {
    throw Object.assign(new Error("템플릿을 찾을 수 없습니다"), {
      code: "TEMPLATE_NOT_FOUND",
    });
  }
  return delay({ ...record }, 180);
}

export async function updateTemplateMock(
  id: number,
  req: {
    messageTemplateName: string;
    channel: Channel;
    messageTemplateBody: string;
    description?: string;
    title?: string;
  },
): Promise<TemplateRecord> {
  seed();
  const existing = store.get(id);
  if (!existing) {
    throw Object.assign(new Error("템플릿을 찾을 수 없습니다"), {
      code: "TEMPLATE_NOT_FOUND",
    });
  }
  if (existing.status !== "DRAFT") {
    throw Object.assign(
      new Error("DRAFT 상태의 템플릿만 수정할 수 있습니다"),
      { code: "TEMPLATE_ALREADY_SENT" },
    );
  }
  const updated: TemplateRecord = {
    ...existing,
    messageTemplateName: req.messageTemplateName,
    channel: req.channel,
    messageTemplateBody: req.messageTemplateBody,
    requiredVariables: extractVariables(req.messageTemplateBody),
    description: req.description,
    title: req.title,
    updatedAt: new Date().toISOString(),
  };
  store.set(id, updated);
  return delay({ ...updated }, 200);
}

export async function deleteTemplateMock(id: number): Promise<{
  messageTemplateId: number;
}> {
  seed();
  const existing = store.get(id);
  if (!existing) {
    throw Object.assign(new Error("템플릿을 찾을 수 없습니다"), {
      code: "TEMPLATE_NOT_FOUND",
    });
  }
  if (existing.status !== "DRAFT") {
    throw Object.assign(
      new Error("DRAFT 상태의 템플릿만 삭제할 수 있습니다"),
      { code: "TEMPLATE_ALREADY_SENT" },
    );
  }
  store.delete(id);
  return delay({ messageTemplateId: id }, 180);
}
