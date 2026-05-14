import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Card,
  Icon,
  PageHeader,
  SearchInput,
  StatCard,
  Tag,
} from "@/components/common";
import { confirmDialog, openModal, toast } from "@/lib/ui-bus";
import { mapEventError } from "@/lib/event-errors";
import {
  useDeleteCategoryMutation,
  useEventCategoriesQuery,
} from "@/hooks/queries/event";
import { useProjectStore } from "@/stores/project";
import type { EventCategorySummary } from "@/api/event";
import AddEventCategoryModal from "./AddEventCategoryModal";
import SdkGuideModal from "./SdkGuideModal";

/**
 * Sprint 7 — 이벤트 목록 페이지.
 *
 * - 카테고리 목록 + StatCard×4
 * - 탭칩 (전체/기본/커스텀) + 검색
 * - 행 클릭 → /p/:pid/events/:categoryName
 */

type FilterKind = "all" | "BUILTIN" | "CUSTOM";

const KIND_LABEL: Record<"BUILTIN" | "CUSTOM", string> = {
  BUILTIN: "기본",
  CUSTOM: "커스텀",
};

function fmtNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`;
  return n.toLocaleString();
}

function fmtDateTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
}

export default function Events() {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;
  const params = useParams<{ projectId: string }>();
  const urlProjectId = params.projectId ? Number(params.projectId) : null;
  const navigate = useNavigate();

  const [filter, setFilter] = useState<FilterKind>("all");
  const [query, setQuery] = useState("");

  const { data, isLoading, isError, error, refetch } = useEventCategoriesQuery(
    projectId,
    projectId != null,
  );

  const deleteMut = useDeleteCategoryMutation(projectId ?? 0);

  const filtered = useMemo<EventCategorySummary[]>(() => {
    const all = data ?? [];
    const byKind =
      filter === "all" ? all : all.filter((c) => c.kind === filter);
    const q = query.trim().toLowerCase();
    return q
      ? byKind.filter(
          (c) =>
            c.categoryName.toLowerCase().includes(q) ||
            c.displayName.toLowerCase().includes(q),
        )
      : byKind;
  }, [data, filter, query]);

  const totals = useMemo(() => {
    const all = data ?? [];
    const total = all.length;
    const builtin = all.filter((c) => c.kind === "BUILTIN").length;
    const custom = all.filter((c) => c.kind === "CUSTOM").length;
    const sumEvents = all.reduce((acc, c) => acc + c.eventCount, 0);
    const avgRate = all.length
      ? all.reduce((acc, c) => acc + c.successRate, 0) / all.length
      : 0;
    return { total, builtin, custom, sumEvents, avgRate };
  }, [data]);

  const onRowOpen = (c: EventCategorySummary): void => {
    if (urlProjectId == null) return;
    navigate(`/p/${urlProjectId}/events/${encodeURIComponent(c.categoryName)}`);
  };

  const onDelete = async (c: EventCategorySummary): Promise<void> => {
    const ok = await confirmDialog({
      title: "이벤트 카테고리를 삭제하시겠습니까?",
      message: `"${c.categoryName}" 카테고리가 삭제됩니다. 사용 중인 세그먼트가 있는지 먼저 확인하세요.`,
      confirmText: "삭제",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await deleteMut.mutateAsync(c.categoryId);
      toast("이벤트 카테고리를 삭제했습니다", "success");
    } catch (err) {
      const m = mapEventError(err);
      toast(m.message, "error");
    }
  };

  return (
    <div>
      <PageHeader
        title="이벤트"
        subtitle="수집 중인 이벤트 카테고리와 속성 스키마를 관리합니다."
        actions={
          <>
            <Button
              variant="secondary"
              size="md"
              icon={Icon.copy(12)}
              onClick={() =>
                openModal(({ close }) => <SdkGuideModal close={close} />)
              }
              style={{}}
            >
              SDK 설치 가이드
            </Button>
            {canEdit && projectId != null && (
              <Button
                variant="primaryStrong"
                size="md"
                icon={Icon.plus(12)}
                onClick={() =>
                  openModal(({ close }) => (
                    <AddEventCategoryModal
                      projectId={projectId}
                      close={close}
                    />
                  ))
                }
                style={{}}
              >
                이벤트 카테고리 추가
              </Button>
            )}
          </>
        }
      />

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(4, 1fr)",
          gap: 14,
          marginBottom: 20,
        }}
      >
        <StatCard
          label="총 이벤트 카테고리"
          value={String(totals.total)}
          sub={`기본 ${totals.builtin} · 커스텀 ${totals.custom}`}
          style={{}}
        />
        <StatCard
          label="30일 누적 수집"
          value={fmtNumber(totals.sumEvents)}
          sub="이벤트 수 (mock)"
          style={{}}
        />
        <StatCard
          label="평균 수집 성공률"
          value={`${totals.avgRate.toFixed(1)}%`}
          sub="카테고리 평균"
          style={{}}
        />
        <StatCard
          label="이상 감지"
          value="0"
          sub="검토 필요 (P1)"
          style={{}}
        />
      </div>

      <Card style={{}}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            marginBottom: 14,
            flexWrap: "wrap",
          }}
        >
          <div
            style={{
              display: "flex",
              gap: 4,
              padding: 3,
              background: "#f1f5f9",
              borderRadius: 8,
            }}
            role="tablist"
            aria-label="유형 필터"
          >
            {(
              [
                ["all", "전체"],
                ["BUILTIN", "기본"],
                ["CUSTOM", "커스텀"],
              ] as Array<[FilterKind, string]>
            ).map(([k, l]) => {
              const active = filter === k;
              return (
                <button
                  key={k}
                  role="tab"
                  aria-selected={active}
                  onClick={() => setFilter(k)}
                  style={{
                    padding: "5px 12px",
                    fontSize: 12,
                    fontWeight: 500,
                    border: 0,
                    borderRadius: 6,
                    background: active ? "#fff" : "transparent",
                    color: active ? "#1f2937" : "#64748b",
                    cursor: "pointer",
                    fontFamily: "inherit",
                    boxShadow: active ? "0 1px 2px rgba(0,0,0,.06)" : "none",
                  }}
                >
                  {l}
                </button>
              );
            })}
          </div>
          <SearchInput
            placeholder="이벤트명 검색 (예: purchase)"
            value={query}
            onChange={setQuery}
            width={280}
          />
        </div>

        {isLoading && (
          <div
            style={{
              padding: 32,
              textAlign: "center",
              color: "#94a3b8",
              fontSize: 13,
            }}
          >
            이벤트 카테고리를 불러오는 중...
          </div>
        )}
        {isError && (
          <div
            role="alert"
            style={{
              padding: 12,
              background: "rgba(239,68,68,.08)",
              border: "1px solid #fecaca",
              borderRadius: 8,
              color: "#b91c1c",
              fontSize: 13,
              marginBottom: 12,
            }}
          >
            {mapEventError(error).message}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => refetch()}
              style={{ marginLeft: 12 }}
            >
              재시도
            </Button>
          </div>
        )}
        {!isLoading && !isError && filtered.length === 0 && (
          <div
            style={{
              padding: 32,
              textAlign: "center",
              color: "#94a3b8",
              fontSize: 13,
            }}
          >
            {(data?.length ?? 0) === 0
              ? "첫 이벤트 카테고리를 추가하고 SDK 로 수집을 시작하세요"
              : "조건에 해당하는 카테고리가 없습니다"}
          </div>
        )}

        {filtered.length > 0 && (
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
                gridTemplateColumns: "2fr 0.8fr 1.2fr 1fr 1fr 50px",
                padding: "12px 16px",
                background: "#f8fafc",
                borderBottom: "1px solid #e2e8f0",
                fontSize: 11,
                color: "#64748b",
                fontWeight: 600,
              }}
            >
              <div>이벤트명</div>
              <div>유형</div>
              <div>30일 수집량</div>
              <div>성공률</div>
              <div>최근 수집</div>
              <div></div>
            </div>
            {filtered.map((c, i) => (
              <div
                key={c.categoryId}
                role="button"
                tabIndex={0}
                onClick={() => onRowOpen(c)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    onRowOpen(c);
                  }
                }}
                style={{
                  display: "grid",
                  gridTemplateColumns: "2fr 0.8fr 1.2fr 1fr 1fr 50px",
                  padding: "14px 16px",
                  alignItems: "center",
                  fontSize: 13,
                  cursor: "pointer",
                  borderBottom:
                    i < filtered.length - 1 ? "1px solid #f1f5f9" : "none",
                  outline: "none",
                  transition: "background 120ms",
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.background = "#f1f5f9";
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = "";
                }}
                onFocus={(e) => {
                  e.currentTarget.style.background = "#eff3fe";
                }}
                onBlur={(e) => {
                  e.currentTarget.style.background = "#fff";
                }}
              >
                <div
                  style={{ display: "flex", alignItems: "center", gap: 10 }}
                >
                  <span
                    style={{
                      width: 28,
                      height: 28,
                      borderRadius: 6,
                      background: "#eff3fe",
                      color: "#3b5bdb",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontSize: 12,
                      fontWeight: 700,
                      fontFamily: "var(--font-mono)",
                    }}
                  >
                    {c.categoryName[0]?.toUpperCase() ?? "?"}
                  </span>
                  <div>
                    <div
                      style={{
                        fontFamily: "var(--font-mono)",
                        color: "#1f2937",
                        fontSize: 13,
                        fontWeight: 500,
                      }}
                    >
                      {c.categoryName}
                    </div>
                    <div style={{ fontSize: 11, color: "#94a3b8" }}>
                      {c.displayName} · 속성 {c.propertyCount}개
                    </div>
                  </div>
                </div>
                <div>
                  <Tag
                    tone={c.kind === "BUILTIN" ? "info" : "soft"}
                    style={{}}
                  >
                    {KIND_LABEL[c.kind]}
                  </Tag>
                </div>
                <div
                  style={{
                    fontVariantNumeric: "tabular-nums",
                    color: "#1f2937",
                  }}
                >
                  {fmtNumber(c.eventCount)}
                </div>
                <div
                  style={{
                    fontVariantNumeric: "tabular-nums",
                    color: "#1f2937",
                  }}
                >
                  {c.successRate.toFixed(1)}%
                </div>
                <div style={{ color: "#64748b", fontSize: 12 }}>
                  <time
                    dateTime={c.lastCollectedAt ?? ""}
                    style={{
                      fontFamily: "var(--font-mono)",
                    }}
                  >
                    {fmtDateTime(c.lastCollectedAt)}
                  </time>
                </div>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "flex-end",
                    gap: 4,
                  }}
                >
                  {canEdit && c.kind === "CUSTOM" && (
                    <button
                      type="button"
                      aria-label="삭제"
                      onClick={(e) => {
                        e.stopPropagation();
                        void onDelete(c);
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.color = "#dc2626";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.color = "#94a3b8";
                      }}
                      style={{
                        border: 0,
                        background: "transparent",
                        color: "#94a3b8",
                        cursor: "pointer",
                      }}
                    >
                      {Icon.trash(13)}
                    </button>
                  )}
                  <span style={{ color: "#cbd5e1" }}>{Icon.chevron(12)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
}
