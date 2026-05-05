import { useNavigate } from "react-router-dom";
import { Card, Icon } from "@/components/common";
import { fmtNum } from "@/utils/fmt.js";
import type { FavoritesResponse } from "@/api/segment";

/**
 * FavoritesStrip — 즐겨찾기 + 최근 사용 두 줄.
 *
 * GET /segments/favorites 응답을 그대로 받아 `favorites` 와 `recentlyUsed` 를 별도 줄로 표시.
 * 클릭 → /p/:projectId/segments/:segmentId 로 navigate.
 */

export interface FavoritesStripProps {
  projectId: number;
  data: FavoritesResponse | undefined;
  isLoading: boolean;
}

export default function FavoritesStrip({
  projectId,
  data,
  isLoading,
}: FavoritesStripProps) {
  const navigate = useNavigate();

  const favorites = data?.favorites ?? [];
  const recently = data?.recentlyUsed ?? [];

  if (!isLoading && favorites.length === 0 && recently.length === 0) {
    return null;
  }

  const goTo = (segmentId: number): void => {
    navigate(`/p/${projectId}/segments/${segmentId}`);
  };

  return (
    <Card style={{ marginBottom: 14 }}>
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        <Row
          icon={<span style={{ color: "#f59e0b" }}>{Icon.star(true, 12)}</span>}
          label="즐겨찾기"
          isLoading={isLoading}
          items={favorites.map((f) => ({
            id: f.segmentId,
            name: f.name,
            estimatedCount: f.estimatedCount,
          }))}
          empty="즐겨찾기한 세그먼트가 없습니다."
          onClick={goTo}
        />
        <Row
          icon={<span style={{ color: "#64748b" }}>{Icon.calendar(12)}</span>}
          label="최근 사용"
          isLoading={isLoading}
          items={recently.map((r) => ({
            id: r.segmentId,
            name: r.name,
            estimatedCount: r.estimatedCount,
          }))}
          empty="최근 사용한 세그먼트가 없습니다."
          onClick={goTo}
        />
      </div>
    </Card>
  );
}

interface ChipItem {
  id: number;
  name: string;
  estimatedCount: number | null;
}

interface RowProps {
  icon: React.ReactNode;
  label: string;
  isLoading: boolean;
  items: ChipItem[];
  empty: string;
  onClick: (id: number) => void;
}

function Row({ icon, label, isLoading, items, empty, onClick }: RowProps) {
  return (
    <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
      <div
        style={{
          width: 88,
          flexShrink: 0,
          display: "inline-flex",
          alignItems: "center",
          gap: 6,
          fontSize: 11,
          fontWeight: 600,
          color: "#475569",
        }}
      >
        {icon}
        <span>{label}</span>
      </div>
      <div
        style={{
          display: "flex",
          gap: 6,
          flexWrap: "wrap",
          flex: 1,
          minWidth: 0,
        }}
      >
        {isLoading ? (
          <span style={{ fontSize: 12, color: "#94a3b8" }}>불러오는 중...</span>
        ) : items.length === 0 ? (
          <span style={{ fontSize: 12, color: "#94a3b8" }}>{empty}</span>
        ) : (
          items.map((it) => (
            <button
              key={it.id}
              type="button"
              onClick={() => onClick(it.id)}
              style={{
                height: 28,
                padding: "0 10px",
                fontSize: 12,
                fontFamily: "inherit",
                background: "#f1f5f9",
                border: "1px solid #e2e8f0",
                borderRadius: 999,
                color: "#1f2937",
                cursor: "pointer",
                display: "inline-flex",
                alignItems: "center",
                gap: 6,
                transition: "background 120ms",
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = "#e2e8f0";
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = "#f1f5f9";
              }}
            >
              <span style={{ fontWeight: 600 }}>{it.name}</span>
              {it.estimatedCount != null && (
                <span
                  style={{
                    color: "#64748b",
                    fontSize: 11,
                    fontVariantNumeric: "tabular-nums",
                  }}
                >
                  · {fmtNum(it.estimatedCount)}
                </span>
              )}
            </button>
          ))
        )}
      </div>
    </div>
  );
}
