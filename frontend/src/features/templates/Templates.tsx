import { useMemo, useState } from "react";
import {
  Button,
  Card,
  ChannelBadge,
  Icon,
  Menu,
  PageHeader,
  SearchInput,
  Select,
} from "@/components/common";
import { ApiError } from "@/api/types";
import { confirmDialog, openModal, toast } from "@/lib/ui-bus";
import {
  useDeleteTemplateMutation,
  useTemplateListQuery,
  type TemplateListQueryParams,
} from "@/hooks/queries/template";
import { useProjectStore } from "@/stores/project";
import type { MessageTemplateListItem } from "@/api/template";
import type { Channel } from "@/schemas/template";
import TemplateDetailModal from "./TemplateDetailModal";
import TemplateFormModal from "./TemplateFormModal";

/**
 * Sprint 4 — Message Template 목록 페이지.
 *
 * - PageHeader + 필터바(검색/채널/정렬) + 카드 그리드(반응형) + 페이지네이션.
 * - 카드 클릭 → 상세 모달, ⋯ 메뉴 → 수정/삭제(EDITOR 이상).
 * - VIEWER 는 [+ 새 템플릿] / ⋯ 메뉴 / 수정-삭제 모두 비노출.
 * - 빈/로딩/에러 상태 분리 처리.
 *
 * 백엔드는 페이징만 지원하므로 채널·검색은 클라이언트 사이드 필터.
 */

type ChannelFilter = "ALL" | Channel;

const CHANNEL_FILTERS: Array<{ value: ChannelFilter; label: string }> = [
  { value: "ALL", label: "전체 채널" },
  { value: "PUSH", label: "PUSH" },
  { value: "EMAIL", label: "EMAIL" },
  { value: "SMS", label: "SMS" },
  { value: "WEBHOOK", label: "WEBHOOK" },
  { value: "KAKAO", label: "KAKAO" },
];

type SortKey = "createdAt,desc" | "createdAt,asc" | "name,asc";

const SORT_OPTIONS: Array<{ value: SortKey; label: string }> = [
  { value: "createdAt,desc", label: "최신순" },
  { value: "createdAt,asc", label: "오래된순" },
  { value: "name,asc", label: "이름순" },
];

function fmtDate(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

function shortenEmail(email: string): string {
  const at = email.indexOf("@");
  if (at < 0) return email;
  return email.slice(0, at);
}

export default function Templates() {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;

  const [q, setQ] = useState("");
  const [channelFilter, setChannelFilter] = useState<ChannelFilter>("ALL");
  const [sort, setSort] = useState<SortKey>("createdAt,desc");
  const [page, setPage] = useState(0);

  const params = useMemo<TemplateListQueryParams>(
    () => ({
      page,
      size: 12,
      channel: channelFilter,
      q: q.trim() || undefined,
      sort,
    }),
    [page, channelFilter, q, sort],
  );

  const { data, isLoading, isError, refetch, isFetching } = useTemplateListQuery(
    projectId,
    params,
  );
  const deleteMut = useDeleteTemplateMutation(projectId ?? -1);

  // 백엔드는 페이징만 지원 — 채널/검색 필터는 받아온 페이지 내에서 클라이언트 사이드로.
  const allItems = data?.messageTemplates ?? [];
  const items = useMemo(() => {
    const lowerQ = q.trim().toLowerCase();
    return allItems.filter((item) => {
      if (channelFilter !== "ALL" && item.channel !== channelFilter) return false;
      if (lowerQ) {
        const hay =
          `${item.messageTemplateName} ${item.messageTitle ?? ""}`.toLowerCase();
        if (!hay.includes(lowerQ)) return false;
      }
      return true;
    });
  }, [allItems, channelFilter, q]);
  const totalPages = data?.pageInfo.totalPages ?? 1;

  const openCreate = (): void => {
    if (!projectId) return;
    openModal(({ close }) => (
      <TemplateFormModal mode="create" projectId={projectId} onClose={close} />
    ));
  };

  const openDetail = (id: number): void => {
    if (!projectId) return;
    openModal(({ close }) => (
      <TemplateDetailModal
        projectId={projectId}
        templateId={id}
        onClose={close}
      />
    ));
  };

  const openEdit = (item: MessageTemplateListItem): void => {
    if (!projectId) return;
    openModal(({ close }) => (
      <TemplateFormModal
        mode="edit"
        projectId={projectId}
        templateId={item.messageTemplateId}
        onClose={close}
      />
    ));
  };

  const onDelete = async (item: MessageTemplateListItem): Promise<void> => {
    const ok = await confirmDialog({
      title: "템플릿을 삭제하시겠습니까?",
      message: `"${item.messageTemplateName}" 템플릿이 영구 삭제됩니다.`,
      confirmText: "삭제",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await deleteMut.mutateAsync(item.messageTemplateId);
      toast("템플릿이 삭제되었습니다", "success");
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : "삭제 중 오류가 발생했습니다";
      toast(msg, "error");
    }
  };

  return (
    <div>
      <PageHeader
        title="메시지 템플릿"
        subtitle="채널별 메시지 양식을 관리하고 변수 치환으로 개인화된 메시지를 보낼 수 있습니다."
        actions={
          canEdit ? (
            <Button
              variant="primaryStrong"
              size="md"
              icon={Icon.plus(12)}
              onClick={openCreate}
              style={{}}
            >
              새 템플릿
            </Button>
          ) : null
        }
      />

      {/* 필터 바 */}
      <div
        style={{
          display: "flex",
          gap: 10,
          alignItems: "center",
          marginBottom: 16,
          flexWrap: "wrap",
        }}
      >
        <SearchInput
          placeholder="이름·본문·설명 검색"
          value={q}
          onChange={(v: string) => {
            setQ(v);
            setPage(0);
          }}
          width={320}
          style={{}}
        />
        <Select
          value={channelFilter}
          options={CHANNEL_FILTERS}
          onChange={(v: string) => {
            setChannelFilter(v as ChannelFilter);
            setPage(0);
          }}
          style={{ minWidth: 160 }}
        />
        <Select
          value={sort}
          options={SORT_OPTIONS}
          onChange={(v: string) => {
            setSort(v as SortKey);
            setPage(0);
          }}
          style={{ minWidth: 140 }}
        />
        {isFetching && !isLoading && (
          <span style={{ fontSize: 11, color: "#94a3b8" }}>새로고침 중...</span>
        )}
      </div>

      {/* 본문 영역 */}
      {isLoading && <SkeletonGrid />}

      {isError && (
        <Card padding={20} style={{}}>
          <div
            role="alert"
            style={{
              padding: 32,
              textAlign: "center",
              color: "#b91c1c",
            }}
          >
            <div style={{ fontSize: 13, marginBottom: 12 }}>
              템플릿을 불러오지 못했습니다.
            </div>
            <Button variant="secondary" size="sm" onClick={() => refetch()} style={{}}>
              재시도
            </Button>
          </div>
        </Card>
      )}

      {!isLoading && !isError && items.length === 0 && (
        <Card padding={20} style={{}}>
          <div
            style={{
              padding: 48,
              textAlign: "center",
              color: "#64748b",
            }}
          >
            <div
              style={{
                fontSize: 14,
                fontWeight: 600,
                color: "#1f2937",
                marginBottom: 6,
              }}
            >
              {q || channelFilter !== "ALL"
                ? "조건에 맞는 템플릿이 없습니다"
                : "첫 템플릿을 만들어보세요"}
            </div>
            <div style={{ fontSize: 12, marginBottom: 16 }}>
              변수 치환을 활용한 개인화 메시지로 캠페인을 시작할 수 있습니다.
            </div>
            {canEdit && !q && channelFilter === "ALL" && (
              <Button
                variant="primaryStrong"
                size="md"
                icon={Icon.plus(12)}
                onClick={openCreate}
                style={{}}
              >
                새 템플릿
              </Button>
            )}
          </div>
        </Card>
      )}

      {!isLoading && !isError && items.length > 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
            gap: 12,
          }}
        >
          {items.map((item) => (
            <TemplateCard
              key={item.messageTemplateId}
              item={item}
              canEdit={canEdit}
              onClick={() => openDetail(item.messageTemplateId)}
              onEdit={() => openEdit(item)}
              onDelete={() => onDelete(item)}
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

      <style>{`@keyframes shimmer { 0%{background-position:200% 0} 100%{background-position:-200% 0} }`}</style>
    </div>
  );
}

interface TemplateCardProps {
  item: MessageTemplateListItem;
  canEdit: boolean;
  onClick: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

function TemplateCard({
  item,
  canEdit,
  onClick,
  onEdit,
  onDelete,
}: TemplateCardProps) {
  // 백엔드 목록 응답에 status 없음 — 수정·삭제는 EDITOR 권한이면 항상 허용.
  const editable = true;

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick();
        }
      }}
      aria-label={`템플릿 ${item.messageTemplateName} 상세 보기`}
      style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 12,
        padding: 16,
        cursor: "pointer",
        display: "flex",
        flexDirection: "column",
        gap: 10,
        transition: "border-color 120ms, box-shadow 120ms",
        outline: "none",
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = "#cbd5e1";
        e.currentTarget.style.boxShadow = "0 4px 12px -4px rgba(0,0,0,.08)";
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = "#e2e8f0";
        e.currentTarget.style.boxShadow = "none";
      }}
      onFocus={(e) => {
        e.currentTarget.style.borderColor = "#3b5bdb";
      }}
      onBlur={(e) => {
        e.currentTarget.style.borderColor = "#e2e8f0";
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          gap: 8,
        }}
      >
        <div
          style={{
            fontSize: 14,
            fontWeight: 700,
            color: "#1f2937",
            lineHeight: 1.4,
            wordBreak: "break-word",
            flex: 1,
            minWidth: 0,
          }}
        >
          {item.messageTemplateName}
        </div>
        {canEdit && (
          <span onClick={(e) => e.stopPropagation()}>
            <Menu
              align="right"
              trigger={
                <button
                  type="button"
                  aria-label="템플릿 작업 메뉴"
                  style={{
                    width: 26,
                    height: 26,
                    border: 0,
                    background: "transparent",
                    color: "#64748b",
                    cursor: "pointer",
                    borderRadius: 4,
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  {Icon.dots(14)}
                </button>
              }
              items={[
                {
                  label: "수정",
                  icon: Icon.edit(12),
                  onClick: onEdit,
                },
                { divider: true },
                {
                  label: "삭제",
                  icon: Icon.trash(12),
                  onClick: onDelete,
                  danger: true,
                },
              ]}
            />
          </span>
        )}
      </div>

      <div
        style={{
          fontSize: 12,
          color: "#64748b",
          lineHeight: 1.5,
          display: "-webkit-box",
          WebkitLineClamp: 2,
          WebkitBoxOrient: "vertical",
          overflow: "hidden",
          minHeight: 36,
        }}
      >
        {item.messageTitle}
      </div>

      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 6,
          flexWrap: "wrap",
          marginTop: "auto",
        }}
      >
        <ChannelBadge channel={item.channel} />
      </div>

      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 6,
          fontSize: 11,
          color: "#94a3b8",
          paddingTop: 8,
          borderTop: "1px solid #f1f5f9",
        }}
      >
        <span>{shortenEmail(item.memberName)}</span>
        <span>·</span>
        <span>{fmtDate(item.createdAt)}</span>
      </div>
    </div>
  );
}

function SkeletonGrid() {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
        gap: 12,
      }}
    >
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          aria-hidden="true"
          style={{
            height: 152,
            background: "#fff",
            border: "1px solid #e2e8f0",
            borderRadius: 12,
            padding: 16,
            position: "relative",
            overflow: "hidden",
          }}
        >
          <div
            style={{
              height: 14,
              width: "60%",
              borderRadius: 4,
              background:
                "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
              backgroundSize: "200% 100%",
              animation: "shimmer 1.4s infinite",
              marginBottom: 10,
            }}
          />
          <div
            style={{
              height: 10,
              width: "90%",
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
              width: "70%",
              borderRadius: 4,
              background:
                "linear-gradient(90deg, #f1f5f9 0%, #e2e8f0 50%, #f1f5f9 100%)",
              backgroundSize: "200% 100%",
              animation: "shimmer 1.4s infinite",
            }}
          />
        </div>
      ))}
    </div>
  );
}
