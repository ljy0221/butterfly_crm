import { api } from "./client";
import {
  SegmentChannelMatrixResponseSchema,
  type Channel,
  type SegmentChannelMatrixResponse,
  type SegmentChannelMatrixSortBy,
} from "@/schemas/report";

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
