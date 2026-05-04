import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Card,
  Icon,
  Menu,
  PageHeader,
  SearchInput,
  Select,
  Tag,
} from "@/components/common";
import { ApiError } from "@/api/types";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { mapCampaignError } from "@/lib/campaign-errors";
import { useProjectStore } from "@/stores/project";
import {
  useCampaignListQuery,
  useDeleteCampaignMutation,
  usePauseCampaignMutation,
  useStartCampaignMutation,
} from "@/hooks/queries/campaign";
import type { CampaignListItem } from "@/api/campaign";
import type { CampaignStatus, Channel } from "@/schemas/campaign";

/**
 * Sprint 6 — Campaign 목록 페이지.
 *
 * - PageHeader + 탭(전체/DRAFT/ACTIVE/PAUSED/ENDED)
 * - SearchInput + 채널 Select + 정렬 Select
 * - 테이블: 이름·설명 / 상태 / 채널 / 세그먼트 / 발송수 / 오픈률 / 다음발송 / ⋯
 * - ⋯ 메뉴: 시작(DRAFT) / 일시정지(ACTIVE) / 삭제(DRAFT) — VIEWER 비노출
 */

const STATUS_LABEL: Record<CampaignStatus, string> = {
  DRAFT: "초안",
  ACTIVE: "진행 중",
  PAUSED: "일시정지",
  ENDED: "종료",
};

const STATUS_TONE: Record<CampaignStatus, "default" | "info" | "success" | "warning" | "error"> = {
  DRAFT: "default",
  ACTIVE: "success",
  PAUSED: "warning",
  ENDED: "default",
};

const CHANNEL_LABEL: Record<Channel, string> = {
  PUSH: "PUSH",
  EMAIL: "EMAIL",
  SMS: "SMS",
  WEBHOOK: "WEBHOOK",
  KAKAO: "KAKAO",
};

type TabKey = "ALL" | CampaignStatus;
type ChannelFilter = "ALL" | Channel;
type SortKey = "createdAt,desc" | "createdAt,asc" | "name,asc";

const TABS: Array<{ key: TabKey; label: string }> = [
  { key: "ALL", label: "전체" },
  { key: "DRAFT", label: "초안" },
  { key: "ACTIVE", label: "진행 중" },
  { key: "PAUSED", label: "일시정지" },
  { key: "ENDED", label: "종료" },
];

const CHANNEL_FILTERS: Array<{ value: ChannelFilter; label: string }> = [
  { value: "ALL", label: "전체 채널" },
  { value: "PUSH", label: "PUSH" },
  { value: "EMAIL", label: "EMAIL" },
  { value: "SMS", label: "SMS" },
  { value: "WEBHOOK", label: "WEBHOOK" },
  { value: "KAKAO", label: "KAKAO" },
];

const SORT_OPTIONS: Array<{ value: SortKey; label: string }> = [
  { value: "createdAt,desc", label: "최신순" },
  { value: "createdAt,asc", label: "오래된순" },
  { value: "name,asc", label: "이름순" },
];

function fmtDateTime(iso: string | null): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", {
    year: "2-digit",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function Campaigns() {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;
  const navigate = useNavigate();

  const [tab, setTab] = useState<TabKey>("ALL");
  const [channelFilter, setChannelFilter] = useState<ChannelFilter>("ALL");
  const [q, setQ] = useState("");
  const [sort, setSort] = useState<SortKey>("createdAt,desc");
  const [page, setPage] = useState(0);

  const params = useMemo(
    () => ({
      status: tab === "ALL" ? undefined : tab,
      channel: channelFilter === "ALL" ? undefined : channelFilter,
      q: q.trim() || undefined,
      sort,
      page,
      size: 20,
    }),
    [tab, channelFilter, q, sort, page],
  );

  const { data, isLoading, isError, refetch, isFetching } = useCampaignListQuery(
    projectId,
    params,
  );
  const startMut = useStartCampaignMutation(projectId ?? -1);
  const pauseMut = usePauseCampaignMutation(projectId ?? -1);
  const deleteMut = useDeleteCampaignMutation(projectId ?? -1);

  const campaigns = data?.campaigns ?? [];
  const totalPages = data?.pagination.totalPages ?? 1;

  const onStart = async (item: CampaignListItem): Promise<void> => {
    const ok = await confirmDialog({
      title: "캠페인을 시작하시겠습니까?",
      message: `"${item.campaignName}" 캠페인을 ACTIVE 로 전환합니다.`,
      confirmText: "발송 시작",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await startMut.mutateAsync(item.campaignId);
      toast("캠페인이 시작되었습니다", "success");
    } catch (err) {
      const m = mapCampaignError(err);
      const fallback = err instanceof ApiError ? err.message : err instanceof Error ? err.message : "오류가 발생했습니다";
      toast(m.message ?? fallback, "error");
    }
  };

  const onPause = async (item: CampaignListItem): Promise<void> => {
    const ok = await confirmDialog({
      title: "캠페인을 일시정지하시겠습니까?",
      message: `"${item.campaignName}" 캠페인을 PAUSED 로 전환합니다.`,
      confirmText: "일시정지",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await pauseMut.mutateAsync(item.campaignId);
      toast("캠페인이 일시정지되었습니다", "success");
    } catch (err) {
      const m = mapCampaignError(err);
      const fallback = err instanceof ApiError ? err.message : err instanceof Error ? err.message : "오류가 발생했습니다";
      toast(m.message ?? fallback, "error");
    }
  };

  const onDelete = async (item: CampaignListItem): Promise<void> => {
    const ok = await confirmDialog({
      title: "캠페인을 삭제하시겠습니까?",
      message: `"${item.campaignName}" 캠페인이 삭제됩니다. (DRAFT 만 삭제 가능)`,
      confirmText: "삭제",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await deleteMut.mutateAsync(item.campaignId);
      toast("캠페인이 삭제되었습니다", "success");
    } catch (err) {
      const m = mapCampaignError(err);
      const fallback = err instanceof ApiError ? err.message : err instanceof Error ? err.message : "오류가 발생했습니다";
      toast(m.message ?? fallback, "error");
    }
  };

  const onCreate = (): void => {
    if (projectId == null) return;
    navigate(`/p/${projectId}/campaigns/new`);
  };

  return (
    <div>
      <PageHeader
        title="캠페인"
        subtitle="세그먼트에 메시지를 발송합니다. 단발·예약·반복 발송을 모두 지원합니다."
        actions={
          canEdit ? (
            <Button variant="primaryStrong" size="md" icon={Icon.plus(12)} onClick={onCreate} style={{}}>
              새 캠페인
            </Button>
          ) : null
        }
      />

      <Card style={{}}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 2,
            marginBottom: 16,
            borderBottom: "1px solid #e2e8f0",
            flexWrap: "wrap",
          }}
        >
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => {
                setTab(t.key);
                setPage(0);
              }}
              style={{
                padding: "10px 14px",
                fontSize: 12,
                fontWeight: 500,
                border: 0,
                background: "transparent",
                color: tab === t.key ? "#1f2937" : "#64748b",
                borderBottom: tab === t.key ? "2px solid #3b5bdb" : "2px solid transparent",
                marginBottom: -1,
                cursor: "pointer",
                fontFamily: "inherit",
              }}
            >
              {t.label}
            </button>
          ))}
        </div>

        <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 14, flexWrap: "wrap" }}>
          <SearchInput
            placeholder="이름·설명 검색"
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
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
              setChannelFilter(e.target.value as ChannelFilter);
              setPage(0);
            }}
            style={{ minWidth: 160 }}
          >
            {CHANNEL_FILTERS.map((c) => (
              <option key={c.value} value={c.value}>
                {c.label}
              </option>
            ))}
          </Select>
          <Select
            value={sort}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
              setSort(e.target.value as SortKey);
              setPage(0);
            }}
            style={{ minWidth: 140 }}
          >
            {SORT_OPTIONS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
          {isFetching && !isLoading && (
            <span style={{ fontSize: 11, color: "#94a3b8" }}>새로고침 중...</span>
          )}
        </div>

        {isLoading && (
          <div style={{ padding: 32, textAlign: "center", color: "#94a3b8", fontSize: 13 }}>
            캠페인 목록을 불러오는 중...
          </div>
        )}

        {isError && (
          <div role="alert" style={{ padding: 32, textAlign: "center", color: "#b91c1c" }}>
            <div style={{ fontSize: 13, marginBottom: 12 }}>캠페인을 불러오지 못했습니다.</div>
            <Button variant="secondary" size="sm" onClick={() => refetch()} style={{}}>
              재시도
            </Button>
          </div>
        )}

        {!isLoading && !isError && campaigns.length === 0 && (
          <div style={{ padding: 48, textAlign: "center", color: "#64748b" }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: "#1f2937", marginBottom: 6 }}>
              {q || tab !== "ALL" || channelFilter !== "ALL"
                ? "조건에 맞는 캠페인이 없습니다"
                : "첫 캠페인을 만들어보세요"}
            </div>
            <div style={{ fontSize: 12, marginBottom: 16 }}>
              세그먼트와 템플릿을 조합해 단발/예약/반복 캠페인을 보낼 수 있습니다.
            </div>
            {canEdit && !q && tab === "ALL" && channelFilter === "ALL" && (
              <Button variant="primaryStrong" size="md" icon={Icon.plus(12)} onClick={onCreate} style={{}}>
                새 캠페인
              </Button>
            )}
          </div>
        )}

        {!isLoading && !isError && campaigns.length > 0 && (
          <div style={{ border: "1px solid #e2e8f0", borderRadius: 10, overflow: "hidden" }}>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "2.4fr 0.9fr 0.7fr 1.2fr 0.8fr 0.8fr 1.2fr 50px",
                padding: "10px 14px",
                background: "#f8fafc",
                borderBottom: "1px solid #e2e8f0",
                fontSize: 11,
                color: "#64748b",
                fontWeight: 600,
              }}
            >
              <div>캠페인명</div>
              <div>상태</div>
              <div>채널</div>
              <div>세그먼트</div>
              <div style={{ textAlign: "right" }}>발송수</div>
              <div style={{ textAlign: "right" }}>오픈률</div>
              <div>다음 발송</div>
              <div></div>
            </div>
            {campaigns.map((c, i) => (
              <CampaignRow
                key={c.campaignId}
                item={c}
                last={i === campaigns.length - 1}
                canEdit={canEdit}
                onClick={() => navigate(`/p/${projectId}/campaigns/${c.campaignId}`)}
                onStart={() => onStart(c)}
                onPause={() => onPause(c)}
                onDelete={() => onDelete(c)}
              />
            ))}
          </div>
        )}

        {totalPages > 1 && !isLoading && !isError && (
          <div style={{ display: "flex", gap: 4, justifyContent: "flex-end", marginTop: 16 }}>
            <Button
              variant="ghost"
              size="sm"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              style={{ opacity: page === 0 ? 0.4 : 1 }}
            >
              이전
            </Button>
            <span style={{ fontSize: 12, color: "#64748b", padding: "6px 8px" }}>
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
    </div>
  );
}

interface CampaignRowProps {
  item: CampaignListItem;
  last: boolean;
  canEdit: boolean;
  onClick: () => void;
  onStart: () => void;
  onPause: () => void;
  onDelete: () => void;
}

function CampaignRow({
  item,
  last,
  canEdit,
  onClick,
  onStart,
  onPause,
  onDelete,
}: CampaignRowProps) {
  const status = item.campaignStatus;

  const menuItems: Array<
    | { label: string; icon?: React.ReactNode; onClick: () => void; danger?: boolean }
    | { divider: true }
  > = [];
  if (canEdit) {
    if (status === "DRAFT") {
      menuItems.push({
        label: "시작",
        icon: Icon.send(12),
        onClick: onStart,
      });
      menuItems.push({ divider: true });
      menuItems.push({
        label: "삭제",
        icon: Icon.trash(12),
        onClick: onDelete,
        danger: true,
      });
    } else if (status === "ACTIVE") {
      menuItems.push({
        label: "일시정지",
        icon: Icon.pause ? Icon.pause(12) : Icon.dots(12),
        onClick: onPause,
      });
    }
  }

  return (
    <div
      onClick={onClick}
      style={{
        display: "grid",
        gridTemplateColumns: "2.4fr 0.9fr 0.7fr 1.2fr 0.8fr 0.8fr 1.2fr 50px",
        padding: "12px 14px",
        alignItems: "center",
        fontSize: 12.5,
        cursor: "pointer",
        borderBottom: last ? "none" : "1px solid #f1f5f9",
        transition: "background 120ms",
      }}
      onMouseEnter={(e) => (e.currentTarget.style.background = "#f8fafc")}
      onMouseLeave={(e) => (e.currentTarget.style.background = "#fff")}
    >
      <div>
        <div style={{ color: "#1f2937", fontWeight: 600 }}>{item.campaignName}</div>
        <div style={{ fontSize: 11, color: "#94a3b8", marginTop: 2 }}>
          #{item.campaignId} · {item.messageTemplateName}
        </div>
      </div>
      <div>
        <Tag tone={STATUS_TONE[status]} dot style={{}}>
          {STATUS_LABEL[status]}
        </Tag>
      </div>
      <div>
        <Tag tone="default" style={{}}>
          {CHANNEL_LABEL[item.channel]}
        </Tag>
      </div>
      <div style={{ color: "#64748b", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
        {item.segmentId == null ? "전체 사용자" : item.segmentName ?? `세그먼트 #${item.segmentId}`}
      </div>
      <div style={{ textAlign: "right", color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>
        {item.sendCount.toLocaleString()}
      </div>
      <div style={{ textAlign: "right", color: item.openRate > 0 ? "#16a34a" : "#94a3b8", fontVariantNumeric: "tabular-nums", fontWeight: 600 }}>
        {item.openRate > 0 ? `${item.openRate.toFixed(1)}%` : "—"}
      </div>
      <div style={{ color: "#64748b", fontSize: 11.5 }}>{fmtDateTime(item.nextRunAt)}</div>
      <div style={{ textAlign: "right" }} onClick={(e) => e.stopPropagation()}>
        {menuItems.length > 0 && (
          <Menu
            align="right"
            trigger={
              <button
                type="button"
                aria-label="캠페인 작업 메뉴"
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
            items={menuItems}
          />
        )}
      </div>
    </div>
  );
}
