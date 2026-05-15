import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Avatar,
  Button,
  Card,
  Icon,
  PageHeader,
  SearchInput,
  Select,
  Tag,
} from "@/components/common";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { mapSegmentError } from "@/lib/segment-errors";
import { useProjectStore } from "@/stores/project";
import { fmtNum } from "@/utils/fmt.js";
import {
  useDeleteSegmentMutation,
  useFavoritesQuery,
  useSegmentListQuery,
  useToggleFavoriteMutation,
  type CreateSegmentVariables,
} from "@/hooks/queries/segment";
import type { BuildStatus, SegmentSummary } from "@/api/segment";
import FavoritesStrip from "./FavoritesStrip";

/**
 * Sprint 5 — Segments 목록 페이지.
 *
 * 백엔드 GET /segments 는 검색·필터·정렬을 지원하지 않으므로
 * 검색(q)/생성자 필터는 클라이언트에서 in-memory 필터링한다.
 *  - sort 는 page 파라미터로 백엔드에 전달
 *  - q / creator 는 받은 content 에서 client-side filter
 */

const PAGE_SIZE = 20;

const SORT_OPTIONS: Array<{ value: string; label: string }> = [
  { value: "createdAt,desc", label: "최신순" },
  { value: "createdAt,asc", label: "오래된순" },
  { value: "updatedAt,desc", label: "최근 수정순" },
  { value: "name,asc", label: "이름순" },
];

const BUILD_STATUS_LABEL: Record<BuildStatus, string> = {
  PENDING: "대기",
  BUILDING: "빌드 중",
  READY: "준비됨",
  FAILED: "실패",
};

const BUILD_STATUS_TONE: Record<
  BuildStatus,
  "default" | "success" | "warning" | "info" | "error"
> = {
  PENDING: "warning",
  BUILDING: "info",
  READY: "success",
  FAILED: "error",
};

export default function Segments() {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;
  const navigate = useNavigate();

  const [q, setQ] = useState("");
  const [sort, setSort] = useState<string>("createdAt,desc");
  const [page, setPage] = useState(0);

  const params = useMemo(
    () => ({ page, size: PAGE_SIZE, sort }),
    [page, sort],
  );

  const {
    data,
    isLoading,
    isError,
    refetch,
    isFetching,
  } = useSegmentListQuery(projectId, params);

  const favoritesQuery = useFavoritesQuery(projectId);

  const deleteMut = useDeleteSegmentMutation(projectId ?? -1);
  const toggleFav = useToggleFavoriteMutation(projectId ?? -1);

  const items = useMemo(() => {
    const all = data?.content ?? [];
    const trimmed = q.trim().toLowerCase();
    if (!trimmed) return all;
    return all.filter(
      (s) =>
        s.name?.toLowerCase().includes(trimmed) ||
        s.description?.toLowerCase().includes(trimmed) ||
        s.createdBy?.name?.toLowerCase().includes(trimmed),
    );
  }, [data, q]);

  const totalPages = data?.totalPages ?? 1;

  const goNew = (): void => {
    if (!projectId || !canEdit) return;
    navigate(`/p/${projectId}/segments/new`);
  };

  const onRowClick = (s: SegmentSummary): void => {
    if (!projectId) return;
    navigate(`/p/${projectId}/segments/${s.segmentId}`);
  };

  const onToggleFavorite = async (s: SegmentSummary): Promise<void> => {
    if (!projectId) return;
    try {
      await toggleFav.mutateAsync({
        segmentId: s.segmentId,
        next: !s.favorited,
      });
    } catch (err) {
      const m = mapSegmentError(err);
      toast(m.message, "error");
    }
  };

  const onDelete = async (s: SegmentSummary): Promise<void> => {
    const ok = await confirmDialog({
      title: "세그먼트를 삭제하시겠습니까?",
      message: `"${s.name}" 세그먼트가 영구 삭제됩니다.${s.linkedCampaignCount > 0 ? ` 연결된 캠페인 ${s.linkedCampaignCount}개가 있을 경우 진행 중 캠페인은 삭제할 수 없습니다.` : ""}`,
      confirmText: "삭제",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await deleteMut.mutateAsync(s.segmentId);
      toast("세그먼트가 삭제되었습니다", "success");
    } catch (err) {
      const m = mapSegmentError(err);
      toast(m.message, "error");
    }
  };

  return (
    <div>
      <PageHeader
        title="세그먼트"
        subtitle="이벤트와 속성을 조합해 타겟 그룹을 정의합니다."
        actions={
          canEdit ? (
            <Button
              variant="primaryStrong"
              size="md"
              icon={Icon.plus(12)}
              onClick={goNew}
              style={{}}
            >
              새 세그먼트
            </Button>
          ) : null
        }
      />

      {projectId && (
        <FavoritesStrip
          projectId={projectId}
          data={favoritesQuery.data}
          isLoading={favoritesQuery.isLoading}
        />
      )}

      <Card style={{}}>
        {/* 필터 바 */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            marginBottom: 14,
            flexWrap: "wrap",
          }}
        >
          <SearchInput
            placeholder="이름·설명·생성자 검색"
            value={q}
            onChange={(v: string) => {
              setQ(v);
            }}
            width={320}
            style={{}}
          />
          <Select
            value={sort}
            options={SORT_OPTIONS}
            onChange={(v: string) => {
              setSort(v);
              setPage(0);
            }}
            style={{ minWidth: 160 }}
          />
          {isFetching && !isLoading && (
            <span style={{ fontSize: 11, color: "#94a3b8" }}>
              새로고침 중...
            </span>
          )}
        </div>

        {/* 본문 영역 */}
        {isLoading && <SkeletonList />}

        {isError && (
          <div
            role="alert"
            style={{ padding: 32, textAlign: "center", color: "#b91c1c" }}
          >
            <div style={{ fontSize: 13, marginBottom: 12 }}>
              세그먼트 목록을 불러오지 못했습니다.
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => refetch()}
              style={{}}
            >
              재시도
            </Button>
          </div>
        )}

        {!isLoading && !isError && items.length === 0 && (
          <div
            style={{ padding: 48, textAlign: "center", color: "#64748b" }}
          >
            <div
              style={{
                fontSize: 14,
                fontWeight: 600,
                color: "#1f2937",
                marginBottom: 6,
              }}
            >
              {q ? "조건에 맞는 세그먼트가 없습니다" : "첫 세그먼트를 만들어보세요"}
            </div>
            <div style={{ fontSize: 12, marginBottom: 16 }}>
              이벤트·사용자·태그·디바이스 조건을 조합해 타겟을 정의할 수 있습니다.
            </div>
            {canEdit && !q && (
              <Button
                variant="primaryStrong"
                size="md"
                icon={Icon.plus(12)}
                onClick={goNew}
                style={{}}
              >
                새 세그먼트
              </Button>
            )}
          </div>
        )}

        {!isLoading && !isError && items.length > 0 && (
          <div
            style={{
              border: "1px solid #e2e8f0",
              borderRadius: 10,
              overflow: "hidden",
            }}
          >
            <div
              style={{
                display: "grid",
                gridTemplateColumns:
                  "30px 2.4fr 1.1fr 0.9fr 0.7fr 0.9fr 100px",
                padding: "12px 16px",
                background: "#f8fafc",
                borderBottom: "1px solid #e2e8f0",
                fontSize: 11,
                color: "#64748b",
                fontWeight: 600,
                alignItems: "center",
              }}
            >
              <div></div>
              <div>세그먼트</div>
              <div>생성자</div>
              <div>예상 모수</div>
              <div>캠페인</div>
              <div>빌드 상태</div>
              <div></div>
            </div>
            {items.map((s, i) => (
              <Row
                key={s.segmentId}
                segment={s}
                isLast={i === items.length - 1}
                canEdit={canEdit}
                onClick={() => onRowClick(s)}
                onToggleFavorite={() => onToggleFavorite(s)}
                onEdit={() => onRowClick(s)}
                onDelete={() => onDelete(s)}
              />
            ))}
          </div>
        )}

        {/* 페이지네이션 */}
        {totalPages > 1 && !isLoading && !isError && (
          <div
            style={{
              display: "flex",
              gap: 4,
              justifyContent: "flex-end",
              marginTop: 16,
            }}
          >
            <Button
              variant="ghost"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              style={{ opacity: page === 0 ? 0.4 : 1 }}
            >
              이전
            </Button>
            <span
              style={{
                fontSize: 12,
                color: "#64748b",
                padding: "6px 8px",
              }}
            >
              {page + 1} / {totalPages}
            </span>
            <Button
              variant="ghost"
              size="sm"
              disabled={page >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              style={{ opacity: page >= totalPages - 1 ? 0.4 : 1 }}
            >
              다음
            </Button>
          </div>
        )}
      </Card>

      <style>{`
        @keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.5} }
      `}</style>
    </div>
  );
}

interface RowProps {
  segment: SegmentSummary;
  isLast: boolean;
  canEdit: boolean;
  onClick: () => void;
  onToggleFavorite: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

function Row({
  segment,
  isLast,
  canEdit,
  onClick,
  onToggleFavorite,
  onEdit,
  onDelete,
}: RowProps) {
  const buildStatus = segment.buildStatus;
  const reachDisplay =
    buildStatus === "PENDING"
      ? "—"
      : buildStatus === "BUILDING"
        ? "..."
        : buildStatus === "FAILED"
          ? "실패"
          : segment.estimatedCount != null
            ? fmtNum(segment.estimatedCount)
            : "—";

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          onClick();
        }
      }}
      style={{
        display: "grid",
        gridTemplateColumns: "30px 2.4fr 1.1fr 0.9fr 0.7fr 0.9fr 40px",
        padding: "14px 16px",
        alignItems: "center",
        fontSize: 13,
        cursor: "pointer",
        borderBottom: isLast ? "none" : "1px solid #f1f5f9",
        transition: "background 120ms",
        outline: "none",
        background: "#fff",
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = "#f8fafc";
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = "#fff";
      }}
      onFocus={(e) => {
        e.currentTarget.style.background = "#f8fafc";
      }}
      onBlur={(e) => {
        e.currentTarget.style.background = "#fff";
      }}
    >
      <div
        onClick={(e) => {
          e.stopPropagation();
          onToggleFavorite();
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.color = "#facc15";
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.color = "#94a3b8";
        }}
        role="button"
        aria-label={
          segment.favorited ? "즐겨찾기에서 제거" : "즐겨찾기에 추가"
        }
        tabIndex={0}
        style={{
          width: 16,
          height: 16,
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {Icon.star(segment.favorited, 16)}
      </div>
      <div style={{ minWidth: 0, paddingRight: 8 }}>
        <div
          style={{
            color: "#1f2937",
            fontWeight: 600,
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {segment.name}
        </div>
        {segment.description && (
          <div
            style={{
              fontSize: 11,
              color: "#94a3b8",
              marginTop: 2,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {segment.description}
          </div>
        )}
      </div>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 6,
          minWidth: 0,
        }}
      >
        <Avatar name={segment.createdBy.name} size={22} style={{}} />
        <span
          style={{
            fontSize: 12,
            color: "#64748b",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
          }}
        >
          {segment.createdBy.name}
        </span>
      </div>
      <div
        style={{
          color: buildStatus === "FAILED" ? "#b91c1c" : "#1f2937",
          fontVariantNumeric: "tabular-nums",
          fontWeight: 500,
        }}
      >
        {buildStatus === "BUILDING" && (
          <span
            aria-hidden
            style={{
              display: "inline-block",
              width: 10,
              height: 10,
              border: "2px solid #e2e8f0",
              borderTopColor: "#3b5bdb",
              borderRadius: 999,
              animation: "spin 0.8s linear infinite",
              marginRight: 6,
              verticalAlign: "middle",
            }}
          />
        )}
        {reachDisplay}
      </div>
      <div>
        <Tag
          tone={segment.linkedCampaignCount > 0 ? "info" : "default"}
          style={{}}
        >
          {segment.linkedCampaignCount}개
        </Tag>
      </div>
      <div>
        <Tag
          tone={BUILD_STATUS_TONE[buildStatus]}
          dot
          style={
            buildStatus === "BUILDING"
              ? { animation: "pulse 1.4s ease-in-out infinite" }
              : {}
          }
        >
          {BUILD_STATUS_LABEL[buildStatus]}
        </Tag>
      </div>
      <div
        onClick={(e) => e.stopPropagation()}
        style={{ display: "flex", justifyContent: "flex-end", alignItems: "center", gap: 6 }}
      >
        {canEdit ? (
          <>
            <SegActionBtn label="수정" onClick={onEdit} />
            <SegActionBtn label="삭제" onClick={onDelete} danger />
          </>
        ) : (
          <span style={{ color: "#cbd5e1" }}>{Icon.chevron(12)}</span>
        )}
      </div>
    </div>
  );
}

function SegActionBtn({
  label,
  onClick,
  danger = false,
}: {
  label: string;
  onClick: () => void;
  danger?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        padding: "4px 10px",
        fontSize: 11,
        fontWeight: 600,
        fontFamily: "inherit",
        borderRadius: 6,
        border: `1px solid ${danger ? "#fecaca" : "#e2e8f0"}`,
        background: danger ? "#fff5f5" : "#f8fafc",
        color: danger ? "#dc2626" : "#374151",
        cursor: "pointer",
        whiteSpace: "nowrap",
        transition: "background 100ms",
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.background = danger ? "#fee2e2" : "#f1f5f9";
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.background = danger ? "#fff5f5" : "#f8fafc";
      }}
    >
      {label}
    </button>
  );
}

function SkeletonList() {
  return (
    <div
      style={{
        border: "1px solid #e2e8f0",
        borderRadius: 10,
        overflow: "hidden",
      }}
    >
      {Array.from({ length: 5 }).map((_, i) => (
        <div
          key={i}
          aria-hidden
          style={{
            padding: 14,
            borderBottom: i === 4 ? "none" : "1px solid #f1f5f9",
            display: "flex",
            gap: 12,
            alignItems: "center",
          }}
        >
          <div
            style={{
              width: 14,
              height: 14,
              borderRadius: 4,
              background:
                "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
              backgroundSize: "200% 100%",
              animation: "shimmer 1.4s infinite",
            }}
          />
          <div style={{ flex: 1 }}>
            <div
              style={{
                height: 14,
                width: "40%",
                borderRadius: 4,
                background:
                  "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
                backgroundSize: "200% 100%",
                animation: "shimmer 1.4s infinite",
                marginBottom: 6,
              }}
            />
            <div
              style={{
                height: 10,
                width: "60%",
                borderRadius: 4,
                background:
                  "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
                backgroundSize: "200% 100%",
                animation: "shimmer 1.4s infinite",
              }}
            />
          </div>
          <div
            style={{
              height: 22,
              width: 60,
              borderRadius: 4,
              background:
                "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
              backgroundSize: "200% 100%",
              animation: "shimmer 1.4s infinite",
            }}
          />
        </div>
      ))}
      <style>{`@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }`}</style>
    </div>
  );
}

// re-export for tests
export type { CreateSegmentVariables };
