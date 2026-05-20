import { api, BASE_URL } from "./client";
import {
  SegmentChannelMatrixResponseSchema,
  type Channel,
  type SegmentChannelMatrixResponse,
  type SegmentChannelMatrixSortBy,
} from "@/schemas/report";
import {
  CampaignFunnelResponseSchema,
  type CampaignFunnelResponse,
} from "@/schemas/funnel";
import { useAuthStore } from "@/stores/auth";

/**
 * CSV 다운로드 공통 유틸.
 * axios 인터셉터가 ApiResponse 래퍼를 unwrap하므로 text/csv 응답엔 raw fetch를 사용.
 * 인증 헤더(Authorization + DEV용 X-Member-Id)는 auth store에서 직접 읽어 첨부.
 */
async function downloadCsv(url: string, filename: string): Promise<void> {
  const state = useAuthStore.getState();
  const headers: Record<string, string> = {};
  const token = state.accessToken;
  if (token) headers["Authorization"] = `Bearer ${token}`;
  if (import.meta.env.DEV && state.member?.memberId != null) {
    headers["X-Member-Id"] = String(state.member.memberId);
  }

  const res = await fetch(BASE_URL + url, { headers });
  if (!res.ok) {
    throw new Error(`CSV 내보내기 실패 (HTTP ${res.status})`);
  }
  const blob = await res.blob();
  const objectUrl = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = objectUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(objectUrl);
}

/**
 * S14P31B101-92 — Reporting (Segment × Channel Matrix) API.
 *
 * 백엔드 패키지: com.b101.butterfly.reporting.report.controller.SegmentChannelMatrixController
 * 경로: GET /api/v1/projects/{projectId}/reports/segment-channel-matrix
 * 권한: @projectPermission.hasRole(#projectId, 'OWNER') — OWNER 전용
 *
 * 응답은 client.ts 인터셉터가 ApiResponse<T> 래퍼를 자동 unwrap.
 * 응답 안전성을 위해 Zod 스키마로 한 번 더 통과시킨다 (ADR-0007 nullable rate 처리 포함).
 */

export type {
  Channel,
  SegmentChannelMatrixResponse,
  SegmentChannelMatrixSortBy,
} from "@/schemas/report";
export type {
  SegmentChannelMatrixRow,
  SegmentChannelMatrixHighlights,
} from "@/schemas/report";

export interface SegmentChannelMatrixParams {
  /** yyyy-MM-dd */
  from: string;
  /** yyyy-MM-dd */
  to: string;
  /** 비어 있으면 모든 채널 포함. */
  channels?: Channel[];
  /** 비어 있으면 모든 세그먼트 포함 (미지정 캠페인 행도 결과에 포함). */
  segmentIds?: number[];
  /** 미지정 시 백엔드 기본값(SENT_COUNT DESC). */
  sortBy?: SegmentChannelMatrixSortBy;
  /** 기본 false (= 내림차순). */
  ascending?: boolean;
}

/**
 * Segment × Channel 성과 매트릭스 조회.
 *
 * Spring `Set<Channel>`/`Set<Long>` 바인딩 컨벤션은 `?channels=PUSH&channels=EMAIL` 형태의
 * 반복 쿼리 파라미터이므로, axios paramsSerializer로 `repeat` 직렬화를 강제한다.
 */
export async function getSegmentChannelMatrix(
  projectId: number,
  params: SegmentChannelMatrixParams,
): Promise<SegmentChannelMatrixResponse> {
  const res = await api.get(
    `/api/v1/projects/${projectId}/reports/segment-channel-matrix`,
    {
      params: {
        from: params.from,
        to: params.to,
        channels: params.channels && params.channels.length > 0
          ? params.channels
          : undefined,
        segmentIds: params.segmentIds && params.segmentIds.length > 0
          ? params.segmentIds
          : undefined,
        sortBy: params.sortBy,
        ascending: params.ascending ?? false,
      },
      paramsSerializer: {
        // axios 1.x: indexes=null → repeat 형태 (?channels=PUSH&channels=EMAIL)
        indexes: null,
      },
    },
  );
  // BigDecimal 비율은 number | string | null 모두 가능 — Zod에서 number | null로 정규화.
  return SegmentChannelMatrixResponseSchema.parse(res.data);
}

// ============================================================
// S14P31B101-90 — Campaign Funnel
// ============================================================

export type { CampaignFunnelResponse, CampaignFunnelStageRow, FunnelStage } from "@/schemas/funnel";

export interface CampaignFunnelParams {
  /** yyyy-MM-dd */
  from: string;
  /** yyyy-MM-dd */
  to: string;
  /** 미지정 시 프로젝트 전체 캠페인 합산. */
  campaignId?: number;
  /** 미지정 시 모든 채널. */
  channel?: Channel;
}

/**
 * 캠페인 단계 펀널 조회 (S14P31B101-90).
 * GET /api/v1/projects/{projectId}/reports/campaign-funnel
 * 권한: OWNER 전용.
 */
export async function getCampaignFunnel(
  projectId: number,
  params: CampaignFunnelParams,
): Promise<CampaignFunnelResponse> {
  const res = await api.get(
    `/api/v1/projects/${projectId}/reports/campaign-funnel`,
    {
      params: {
        from: params.from,
        to: params.to,
        campaignId: params.campaignId,
        channel: params.channel,
      },
    },
  );
  return CampaignFunnelResponseSchema.parse(res.data);
}

// ============================================================
// S14P31B101-91 — CSV 내보내기
// ============================================================

/**
 * 세그먼트×채널 매트릭스 CSV 다운로드.
 * GET /api/v1/projects/{projectId}/reports/segment-channel-matrix/export
 */
export async function exportSegmentChannelMatrixCsv(
  projectId: number,
  params: SegmentChannelMatrixParams,
): Promise<void> {
  const qs = new URLSearchParams();
  qs.set("from", params.from);
  qs.set("to", params.to);
  if (params.channels) params.channels.forEach((c) => qs.append("channels", c));
  if (params.segmentIds) params.segmentIds.forEach((id) => qs.append("segmentIds", String(id)));
  if (params.sortBy) qs.set("sortBy", params.sortBy);
  qs.set("ascending", String(params.ascending ?? false));
  await downloadCsv(
    `/api/v1/projects/${projectId}/reports/segment-channel-matrix/export?${qs}`,
    `segment-channel-matrix-${projectId}.csv`,
  );
}

/**
 * 캠페인 펀널 CSV 다운로드.
 * GET /api/v1/projects/{projectId}/reports/campaign-funnel/export
 */
export async function exportCampaignFunnelCsv(
  projectId: number,
  params: CampaignFunnelParams,
): Promise<void> {
  const qs = new URLSearchParams();
  qs.set("from", params.from);
  qs.set("to", params.to);
  if (params.campaignId != null) qs.set("campaignId", String(params.campaignId));
  if (params.channel) qs.set("channel", params.channel);
  await downloadCsv(
    `/api/v1/projects/${projectId}/reports/campaign-funnel/export?${qs}`,
    `campaign-funnel-${projectId}.csv`,
  );
}
