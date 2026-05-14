import { useMemo, useState } from "react";
import {
  useFavoritesQuery,
  useSegmentListQuery,
} from "@/hooks/queries/segment";
import type { SegmentSummary } from "@/api/segment";
import type { FieldErrorMap, WizardDraft } from "./wizardReducer";

/**
 * Step 3 — 세그먼트 선택.
 * - 검색 + 즐겨찾기 칩 (Sprint 5 useFavoritesQuery 재사용)
 * - 단일 선택 + "전체 사용자 발송" 옵션 (segmentId=null)
 */

export interface Step3SegmentProps {
  projectId: number;
  draft: WizardDraft;
  errors: FieldErrorMap;
  onSelect: (segmentId: number | null, segmentName: string | null) => void;
}

export default function Step3Segment({
  projectId,
  draft,
  errors,
  onSelect,
}: Step3SegmentProps) {
  const [q, setQ] = useState("");
  const { data: favoritesData } = useFavoritesQuery(projectId);
  const { data: listData, isLoading, isError, refetch } = useSegmentListQuery(
    projectId,
    { page: 0, size: 50, sort: "createdAt,desc" },
  );

  const filtered = useMemo(() => {
    const all = listData?.content ?? [];
    const ql = q.trim().toLowerCase();
    if (!ql) return all;
    return all.filter(
      (s) =>
        s.name?.toLowerCase().includes(ql) ||
        s.description?.toLowerCase().includes(ql),
    );
  }, [listData?.content, q]);

  const favorites = favoritesData?.favorites ?? [];

  return (
    <div>
      <h2 style={titleStyle}>세그먼트 선택</h2>
      <p style={subtitleStyle}>
        발송 대상을 선택하세요. 세그먼트를 지정하지 않으면 프로젝트 전체 사용자에게 발송됩니다.
      </p>

      {errors.segmentId && (
        <div role="alert" style={errorBanner}>
          {errors.segmentId}
        </div>
      )}

      {/* 전체 사용자 카드 */}
      <button
        type="button"
        role="radio"
        aria-checked={draft.segmentId === null}
        onClick={() => onSelect(null, null)}
        style={{
          ...optionCardStyle(draft.segmentId === null),
          width: "100%",
          marginBottom: 14,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <span style={{ fontSize: 26 }} aria-hidden="true">
            👥
          </span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: draft.segmentId === null ? "#3b5bdb" : "#1f2937" }}>
              전체 사용자에게 발송
            </div>
            <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>
              세그먼트 없이 프로젝트의 모든 사용자에게 발송됩니다.
            </div>
          </div>
        </div>
      </button>

      {/* 즐겨찾기 칩 */}
      {favorites.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          <div style={{ fontSize: 11, fontWeight: 600, color: "#94a3b8", marginBottom: 8, textTransform: "uppercase", letterSpacing: "0.05em" }}>
            ⭐ 즐겨찾기
          </div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
            {favorites.map((f) => {
              const active = draft.segmentId === f.segmentId;
              return (
                <button
                  key={f.segmentId}
                  type="button"
                  onClick={() => onSelect(f.segmentId, f.name)}
                  style={{
                    fontSize: 12,
                    padding: "5px 10px",
                    borderRadius: 999,
                    border: "1px solid",
                    borderColor: active ? "#3b5bdb" : "#e2e8f0",
                    background: active ? "rgba(59,91,219,0.08)" : "#fff",
                    color: active ? "#3b5bdb" : "#334155",
                    cursor: "pointer",
                    fontFamily: "inherit",
                    fontWeight: active ? 600 : 500,
                  }}
                >
                  {f.name}
                  {f.estimatedCount != null && (
                    <span style={{ marginLeft: 6, color: active ? "#3b5bdb" : "#94a3b8" }}>
                      · {f.estimatedCount.toLocaleString()}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>
      )}

      {/* 검색 */}
      <input
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="세그먼트 이름·설명 검색"
        style={{
          width: "100%",
          height: 36,
          padding: "0 12px",
          fontFamily: "inherit",
          fontSize: 13,
          border: "1px solid #e2e8f0",
          borderRadius: 8,
          marginBottom: 12,
          outline: "none",
          boxSizing: "border-box",
        }}
      />

      {isLoading && (
        <div style={{ padding: 24, textAlign: "center", color: "#94a3b8", fontSize: 12 }}>
          세그먼트 목록을 불러오는 중...
        </div>
      )}

      {isError && (
        <div style={errorBanner}>
          세그먼트 목록을 불러오지 못했습니다.{" "}
          <button
            type="button"
            onClick={() => refetch()}
            style={{ marginLeft: 4, color: "#3b5bdb", background: "transparent", border: 0, cursor: "pointer", textDecoration: "underline" }}
          >
            재시도
          </button>
        </div>
      )}

      {!isLoading && !isError && filtered.length === 0 && (
        <div style={{ padding: 24, textAlign: "center", color: "#94a3b8", fontSize: 12 }}>
          {q
            ? "조건에 맞는 세그먼트가 없습니다"
            : "프로젝트에 생성된 세그먼트가 없습니다 (전체 사용자 발송으로 진행 가능)"}
        </div>
      )}

      {!isLoading && !isError && filtered.length > 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: 10,
            maxHeight: 360,
            overflowY: "auto",
            padding: 2,
          }}
        >
          {filtered.map((s) => (
            <SegmentCard
              key={s.segmentId}
              segment={s}
              active={draft.segmentId === s.segmentId}
              onSelect={() => onSelect(s.segmentId, s.name)}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function SegmentCard({
  segment,
  active,
  onSelect,
}: {
  segment: SegmentSummary;
  active: boolean;
  onSelect: () => void;
}) {
  const ready = segment.buildStatus === "READY";
  return (
    <button
      type="button"
      role="radio"
      aria-checked={active}
      onClick={onSelect}
      style={optionCardStyle(active)}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 6 }}>
        <span style={{ fontSize: 13.5, fontWeight: 700, color: active ? "#3b5bdb" : "#1f2937" }}>
          {segment.name}
        </span>
        {!ready && (
          <span style={{ fontSize: 10, padding: "1px 6px", borderRadius: 4, background: "#f1f5f9", color: "#94a3b8", marginLeft: "auto" }}>
            {segment.buildStatus}
          </span>
        )}
      </div>
      <div
        style={{
          fontSize: 11.5,
          color: "#64748b",
          marginBottom: 8,
          display: "-webkit-box",
          WebkitLineClamp: 2,
          WebkitBoxOrient: "vertical",
          overflow: "hidden",
          minHeight: 30,
        }}
      >
        {segment.description || "(설명 없음)"}
      </div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: 11, color: "#94a3b8" }}>
        <span>
          {ready && segment.estimatedCount != null
            ? `예상 ${segment.estimatedCount.toLocaleString()}명`
            : "예상 수치 없음"}
        </span>
        <span>연결 {segment.linkedCampaignCount}</span>
      </div>
    </button>
  );
}

function optionCardStyle(active: boolean): React.CSSProperties {
  return {
    padding: 14,
    borderRadius: 10,
    border: "1px solid",
    borderColor: active ? "#3b5bdb" : "#e2e8f0",
    background: active ? "rgba(59,91,219,0.06)" : "#fff",
    cursor: "pointer",
    fontFamily: "inherit",
    textAlign: "left",
    transition: "border-color 120ms",
  };
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
  marginBottom: 20,
  fontSize: 13,
  color: "#64748b",
  lineHeight: 1.5,
};

const errorBanner: React.CSSProperties = {
  marginBottom: 14,
  padding: 10,
  background: "rgba(239,68,68,.08)",
  border: "1px solid rgba(239,68,68,.25)",
  borderRadius: 8,
  color: "#b91c1c",
  fontSize: 12.5,
};
