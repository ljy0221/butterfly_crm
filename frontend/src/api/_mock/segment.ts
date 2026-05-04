import { delay } from "./index";
import { getCampaignsBySegmentId } from "./campaign";

/**
 * Sprint 5 — Segment 도메인 mock 어댑터.
 *
 * 백엔드 미구현 3개:
 *  - GET /segments/{id}/users         (Phase 8) — 빈 페이지 반환
 *  - POST /segments/{id}/rebuild      (Phase 6) — BUILDING 즉시 반환 후 5초 뒤 자동 READY 전이
 *  - GET /segments/{id}/campaigns     (Phase 7) — 빈 4-탭 반환
 */

// ---------- 빌드 상태 ----------

export type BuildStatus = "PENDING" | "BUILDING" | "READY" | "FAILED";

const BUILD_STATUS_BY_SEGMENT: Map<number, BuildStatus> = new Map();

/**
 * 현재 mock 빌드 상태 조회. 미등록이면 READY (default).
 */
export function getBuildStatusMock(segmentId: number): BuildStatus {
  return BUILD_STATUS_BY_SEGMENT.get(segmentId) ?? "READY";
}

// ---------- DTO ----------

export interface SegmentUserMockItem {
  userId: number;
  externalId: string;
  email: string;
  lastActiveAt: string;
  matchedAt: string;
}

export interface PageResponseMock<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RebuildResponseMock {
  segmentId: number;
  buildStatus: BuildStatus;
  queuedAt: string;
}

export interface LinkedCampaignSummaryMock {
  campaignId: number;
  name: string;
  campaignStatus: string;
}

export interface LinkedCampaignsResponseMock {
  active: LinkedCampaignSummaryMock[];
  paused: LinkedCampaignSummaryMock[];
  draft: LinkedCampaignSummaryMock[];
  ended: LinkedCampaignSummaryMock[];
  totalCount: number;
}

// ---------- 어댑터 함수 ----------

/**
 * GET /segments/{id}/users mock — 항상 빈 페이지.
 * Phase 8 (event.users JOIN) 구현 전까지 UI 가 "Phase 8 출시 예정" 안내를 보여줌.
 */
export async function listSegmentUsersMock(
  _segmentId: number,
  page: number = 0,
  size: number = 20,
): Promise<PageResponseMock<SegmentUserMockItem>> {
  return delay(
    {
      content: [],
      page,
      size,
      totalElements: 0,
      totalPages: 0,
    },
    180,
  );
}

/**
 * POST /segments/{id}/rebuild mock.
 *
 * 호출 직후 BUILDING 으로 즉시 응답하고, 5초 뒤 READY 로 자동 전이한다.
 * UI 측에서 5s setTimeout invalidate 하면 다음 detail 조회 시 READY 가 보인다.
 */
export async function rebuildSegmentMock(
  segmentId: number,
): Promise<RebuildResponseMock> {
  BUILD_STATUS_BY_SEGMENT.set(segmentId, "BUILDING");
  // 5초 후 무조건 READY 로 전이
  setTimeout(() => {
    BUILD_STATUS_BY_SEGMENT.set(segmentId, "READY");
  }, 5_000);
  return delay(
    {
      segmentId,
      buildStatus: "BUILDING" as const,
      queuedAt: new Date().toISOString(),
    },
    180,
  );
}

/**
 * GET /segments/{id}/campaigns mock.
 * Sprint 6 — campaign mock store 의 캠페인을 segmentId 매칭으로 4-tab 그룹화.
 * 순환 import 방지: campaign → segment 단방향. segment 측은 getCampaignsBySegmentId 만 import.
 */
export async function getLinkedCampaignsMock(
  segmentId: number,
): Promise<LinkedCampaignsResponseMock> {
  const grouped = getCampaignsBySegmentId(segmentId);
  return delay(
    {
      active: grouped.active,
      paused: grouped.paused,
      draft: grouped.draft,
      ended: grouped.ended,
      totalCount: grouped.totalCount,
    },
    180,
  );
}
