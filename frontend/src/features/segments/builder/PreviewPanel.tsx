import { useMemo } from "react";
import { Button, Card, Icon, Tag } from "@/components/common";
import { ApiError } from "@/api/types";
import type { PreviewResponse } from "@/api/segment";
import { mapSegmentError } from "@/lib/segment-errors";
import { fmtNum } from "@/utils/fmt.js";

/**
 * PreviewPanel — 미리보기 실행 + 결과 표시.
 *
 * - 초기: "미리보기를 실행해 모수를 확인하세요" + [미리보기]
 * - 응답: estimatedCount, method (HLL/EXACT), estimatedAt
 * - 에러: SEGMENT_501_EVENT_NOT_READY 등 한국어 매핑
 * - dirty=true 일 때 "조건이 변경됨, 재실행 권장" 경고
 */

export interface PreviewPanelProps {
  preview: PreviewResponse | null;
  isLoading: boolean;
  error: unknown;
  /** 트리가 미리보기 후 변경되었는지. */
  dirtyAfterPreview: boolean;
  /** 미리보기 클릭 핸들러. */
  onRunPreview: () => void;
  /** 미리보기 실행 가능 여부 (조건 트리가 비어 있으면 false). */
  canPreview: boolean;
  disabled?: boolean;
}

function formatRelativeTime(iso: string): string {
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return iso;
  const diff = Date.now() - t;
  const sec = Math.floor(diff / 1000);
  if (sec < 5) return "방금 전";
  if (sec < 60) return `${sec}초 전`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}분 전`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}시간 전`;
  const day = Math.floor(hr / 24);
  return `${day}일 전`;
}

export default function PreviewPanel({
  preview,
  isLoading,
  error,
  dirtyAfterPreview,
  onRunPreview,
  canPreview,
  disabled,
}: PreviewPanelProps) {
  const errorMapped = useMemo(() => {
    if (!error) return null;
    return mapSegmentError(error);
  }, [error]);

  return (
    <Card
      title="예상 모수"
      subtitle="조건에 매칭되는 사용자 수를 추정합니다"
      style={{}}
    >
      {/* 결과 영역 */}
      {!preview && !isLoading && !errorMapped && (
        <div
          style={{
            padding: "8px 0 14px",
            fontSize: 13,
            color: "#64748b",
            lineHeight: 1.6,
          }}
        >
          미리보기를 실행해 모수를 확인하세요.
        </div>
      )}

      {isLoading && (
        <div
          style={{
            padding: "16px 0",
            display: "flex",
            alignItems: "center",
            gap: 10,
            color: "#64748b",
          }}
        >
          <span
            aria-hidden
            style={{
              width: 14,
              height: 14,
              border: "2px solid #e2e8f0",
              borderTopColor: "#3b5bdb",
              borderRadius: 999,
              animation: "spin 0.8s linear infinite",
              display: "inline-block",
            }}
          />
          <span style={{ fontSize: 13 }}>예상 모수 계산 중...</span>
        </div>
      )}

      {errorMapped && !isLoading && (
        <div
          role="alert"
          style={{
            padding: 12,
            background: "rgba(239,68,68,.06)",
            border: "1px solid rgba(239,68,68,.25)",
            borderRadius: 8,
            color: "#b91c1c",
            fontSize: 12.5,
            display: "flex",
            flexDirection: "column",
            gap: 8,
          }}
        >
          <span>{errorMapped.message}</span>
          {error instanceof ApiError && (
            <span style={{ fontSize: 11, color: "#7f1d1d" }}>
              {error.code}
            </span>
          )}
        </div>
      )}

      {preview && !isLoading && !errorMapped && (
        <div style={{ paddingBottom: 4 }}>
          <div
            style={{
              display: "flex",
              alignItems: "baseline",
              gap: 6,
            }}
          >
            <div
              style={{
                fontSize: 32,
                fontWeight: 700,
                color: "#1f2937",
                fontFamily: "var(--font-display)",
                fontVariantNumeric: "tabular-nums",
                letterSpacing: "-0.02em",
                lineHeight: 1,
              }}
            >
              {fmtNum(preview.estimatedCount)}
            </div>
            <div style={{ fontSize: 13, color: "#64748b" }}>명</div>
          </div>
          <div
            style={{
              marginTop: 8,
              display: "flex",
              alignItems: "center",
              gap: 8,
              flexWrap: "wrap",
            }}
          >
            <Tag tone={preview.method === "HLL" ? "info" : "success"} style={{}}>
              {preview.method === "HLL" ? "HLL 추정" : "정확 (EXACT)"}
            </Tag>
            <span style={{ fontSize: 11, color: "#94a3b8" }}>
              {formatRelativeTime(preview.estimatedAt)}
            </span>
          </div>
        </div>
      )}

      {/* dirty 경고 */}
      {dirtyAfterPreview && preview && !isLoading && (
        <div
          style={{
            marginTop: 10,
            padding: 8,
            background: "rgba(245,158,11,.10)",
            border: "1px solid rgba(245,158,11,.25)",
            borderRadius: 6,
            color: "#b45309",
            fontSize: 11.5,
            display: "flex",
            alignItems: "center",
            gap: 6,
          }}
        >
          <span>{Icon.calendar(11)}</span>
          조건이 변경되었습니다. 미리보기 재실행 후 저장하세요.
        </div>
      )}

      {/* 액션 버튼 */}
      <Button
        variant={preview ? "secondary" : "primaryStrong"}
        size="md"
        onClick={onRunPreview}
        disabled={disabled || isLoading || !canPreview}
        style={{
          marginTop: 14,
          width: "100%",
          justifyContent: "center",
          opacity: disabled || !canPreview ? 0.5 : 1,
        }}
      >
        {isLoading ? "계산 중..." : preview ? "다시 미리보기" : "미리보기 실행"}
      </Button>
      {!canPreview && (
        <div
          style={{
            marginTop: 8,
            fontSize: 11,
            color: "#94a3b8",
            textAlign: "center",
          }}
        >
          조건을 1개 이상 추가하세요.
        </div>
      )}

      <style>{`@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }`}</style>
    </Card>
  );
}
