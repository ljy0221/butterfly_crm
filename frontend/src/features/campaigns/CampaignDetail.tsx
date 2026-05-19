import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Button,
  Card,
  Icon,
  PageHeader,
  StatCard,
  Tag,
} from "@/components/common";
import { ApiError } from "@/api/types";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { mapCampaignError } from "@/lib/campaign-errors";
import { useProjectStore } from "@/stores/project";
import { useTemplateDetailQuery } from "@/hooks/queries/template";
import {
  useCampaignDetailQuery,
  useDeleteCampaignMutation,
  usePauseCampaignMutation,
  useStartCampaignMutation,
} from "@/hooks/queries/campaign";
import { useCreditBalanceQuery } from "@/hooks/queries/payment";
import { useSegmentDetailQuery } from "@/hooks/queries/segment";
import { useQuery } from "@tanstack/react-query";
import { getCampaignStats } from "@/api/analytics";
import { describeRecurring } from "@/lib/cron-preview";
import { fromUtcIsoToLocal } from "@/schemas/campaign";
import type { CampaignDetail as CampaignDetailType } from "@/api/campaign";
import type { CampaignStatus, Channel } from "@/schemas/campaign";
import ExecutionsTable from "./ExecutionsTable";
import MobilePreviewPane from "./MobilePreviewPane";
import NotificationLogTable from "@/features/notifications/NotificationLogTable";

/**
 * Sprint 6 — Campaign 상세 페이지.
 *
 * 상태별 액션 분기 (액션 매트릭스 ui.md §2.3):
 *  DRAFT  → 수정 / 삭제 / 발송 시작
 *  ACTIVE → 일시정지
 *  PAUSED → (재개 P1)
 *  ENDED  → 액션 없음
 *
 * VIEWER 는 모든 액션 disabled.
 *
 * 좌(1fr): 기본정보 / 채널·세그먼트 / 템플릿 / 일정 / 실행 이력
 * 우(340px sticky): MobilePreviewPane
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

function fmtDateTime(iso: string | null | undefined): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function CampaignDetail() {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;
  const navigate = useNavigate();
  const params = useParams<{ campaignId: string }>();
  const campaignId = params.campaignId ? Number(params.campaignId) : NaN;

  if (projectId == null) {
    return <Navigate to="/projects" replace />;
  }
  if (Number.isNaN(campaignId)) {
    return <Navigate to={`/p/${projectId}/campaigns`} replace />;
  }

  // ACTIVE 상태일 땐 1.5초 폴링으로 실행 이력 새로고침 (mock SUCCESS 전이 보이게)
  const { data: campaign, isLoading, isError, refetch } = useCampaignDetailQuery(
    projectId,
    campaignId,
    true,
  );

  const startMut = useStartCampaignMutation(projectId);
  const pauseMut = usePauseCampaignMutation(projectId);
  const deleteMut = useDeleteCampaignMutation(projectId);

  const isOwner = role === "OWNER";
  const isDraftStatus = campaign?.campaignStatus === "DRAFT";
  const { data: segmentDetail } = useSegmentDetailQuery(
    projectId,
    campaign?.segmentId ?? null,
    isDraftStatus && campaign?.segmentId != null,
  );
  const { data: balance } = useCreditBalanceQuery(projectId, isOwner && isDraftStatus);
  const segmentAudienceCount = campaign?.segmentId != null
    ? (segmentDetail?.estimatedCount ?? null)
    : null;
  const isZeroAudience = segmentAudienceCount === 0;
  const isInsufficientBalance = isOwner
    && balance?.availableBalance != null
    && segmentAudienceCount != null
    && balance.availableBalance < segmentAudienceCount;

  const onStart = async (): Promise<void> => {
    if (!campaign) return;
    if (isZeroAudience) {
      toast("발송 대상이 0명입니다. 세그먼트를 변경한 후 다시 시도해주세요.", "error");
      return;
    }
    if (isInsufficientBalance) {
      toast("잔액이 부족하여 캠페인을 시작할 수 없습니다.", "error");
      return;
    }
    const ok = await confirmDialog({
      title: "캠페인을 시작하시겠습니까?",
      message: "캠페인이 ACTIVE 상태로 전환됩니다.",
      confirmText: "발송 시작",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await startMut.mutateAsync(campaignId);
      toast("캠페인이 시작되었습니다", "success");
    } catch (err) {
      const m = mapCampaignError(err);
      const fallback = err instanceof ApiError ? err.message : err instanceof Error ? err.message : "시작 중 오류가 발생했습니다";
      toast(m.message ?? fallback, "error");
    }
  };

  const onPause = async (): Promise<void> => {
    if (!campaign) return;
    const ok = await confirmDialog({
      title: "캠페인을 일시정지하시겠습니까?",
      message: "예약/반복 발송이 일시중단됩니다. 이후 [재개] 액션은 P1 입니다.",
      confirmText: "일시정지",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await pauseMut.mutateAsync(campaignId);
      toast("캠페인이 일시정지되었습니다", "success");
    } catch (err) {
      const m = mapCampaignError(err);
      const fallback = err instanceof ApiError ? err.message : err instanceof Error ? err.message : "일시정지 중 오류가 발생했습니다";
      toast(m.message ?? fallback, "error");
    }
  };

  const onDelete = async (): Promise<void> => {
    if (!campaign) return;
    const ok = await confirmDialog({
      title: "캠페인을 삭제하시겠습니까?",
      message: `"${campaign.campaignName}" 캠페인이 삭제됩니다. (DRAFT 상태에서만 삭제 가능)`,
      confirmText: "삭제",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await deleteMut.mutateAsync(campaignId);
      toast("캠페인이 삭제되었습니다", "success");
      navigate(`/p/${projectId}/campaigns`);
    } catch (err) {
      const m = mapCampaignError(err);
      const fallback = err instanceof ApiError ? err.message : err instanceof Error ? err.message : "삭제 중 오류가 발생했습니다";
      toast(m.message ?? fallback, "error");
    }
  };

  if (isLoading) {
    return (
      <div>
        <PageHeader title="캠페인" subtitle="로딩 중..." style={{}} />
        <div style={{ padding: 32, textAlign: "center", color: "#94a3b8", fontSize: 13 }}>
          캠페인을 불러오는 중...
        </div>
      </div>
    );
  }
  if (isError || !campaign) {
    return (
      <div>
        <PageHeader title="캠페인" subtitle="오류" style={{}} />
        <Card style={{}} padding={24}>
          <div role="alert" style={{ color: "#b91c1c", fontSize: 13, marginBottom: 12 }}>
            캠페인을 불러오지 못했습니다.
          </div>
          <Button variant="secondary" size="sm" onClick={() => refetch()} style={{}}>
            재시도
          </Button>
        </Card>
      </div>
    );
  }

  return (
    <CampaignDetailBody
      campaign={campaign}
      canEdit={canEdit}
      projectId={projectId}
      campaignId={campaignId}
      onStart={onStart}
      onPause={onPause}
      onDelete={onDelete}
      onBack={() => navigate(`/p/${projectId}/campaigns`)}
      onEdit={() => navigate(`/p/${projectId}/campaigns/${campaignId}/edit`)}
      isZeroAudience={isZeroAudience}
      isInsufficientBalance={isInsufficientBalance}
    />
  );
}

interface CampaignDetailBodyProps {
  campaign: CampaignDetailType;
  canEdit: boolean;
  projectId: number;
  campaignId: number;
  onStart: () => void;
  onPause: () => void;
  onDelete: () => void;
  onBack: () => void;
  onEdit: () => void;
  isZeroAudience: boolean;
  isInsufficientBalance: boolean;
}

function CampaignDetailBody({
  campaign,
  canEdit,
  projectId,
  campaignId,
  onStart,
  onPause,
  onDelete,
  onBack,
  onEdit,
  isZeroAudience,
  isInsufficientBalance,
}: CampaignDetailBodyProps) {
  // ?tab=overview|logs (Sprint 7)
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get("tab");
  const tab: "overview" | "logs" = tabParam === "logs" ? "logs" : "overview";
  const setTab = (next: "overview" | "logs") => {
    const sp = new URLSearchParams(searchParams);
    sp.set("tab", next);
    setSearchParams(sp, { replace: true });
  };

  // 통계 (campaign mock-fallback 시 mock store 기반 더미)
  const { data: stats } = useQuery({
    queryKey: ["campaignStats", projectId, campaignId],
    queryFn: () => getCampaignStats(projectId, campaignId),
  });

  const { data: template } = useTemplateDetailQuery(
    projectId,
    campaign.messageTemplateId,
    true,
  );

  const status = campaign.campaignStatus;
  const isDraft = status === "DRAFT";
  const isActive = status === "ACTIVE";

  const sent = stats?.sentCount ?? 0;
  const delivered = stats?.deliveredCount ?? 0;
  const clicked = stats?.clickedCount ?? 0;
  const failed = stats?.failedCount ?? 0;

  return (
    <div>
      <PageHeader
        back="캠페인 목록"
        onBack={onBack}
        title={campaign.campaignName}
        subtitle={
          <span style={{ display: "inline-flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <Tag tone={STATUS_TONE[status]} dot style={{}}>
              {STATUS_LABEL[status]}
            </Tag>
            <Tag tone="default" style={{}}>
              {CHANNEL_LABEL[campaign.channel]}
            </Tag>
          </span>
        }
        actions={
          <>
            {canEdit && isDraft && (
              <>
                <Button variant="ghost" size="md" icon={Icon.trash(12)} onClick={onDelete} style={{}}>
                  삭제
                </Button>
                <Button variant="secondary" size="md" icon={Icon.edit(12)} onClick={onEdit} style={{}}>
                  수정
                </Button>
                <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 4 }}>
                  <Button
                    variant="primaryStrong"
                    size="md"
                    onClick={onStart}
                    disabled={isZeroAudience || isInsufficientBalance}
                    style={{}}
                  >
                    발송 시작
                  </Button>
                  {isZeroAudience && (
                    <span style={{ fontSize: 11, color: "#c2410c" }}>발송 대상이 0명입니다</span>
                  )}
                  {!isZeroAudience && isInsufficientBalance && (
                    <span style={{ fontSize: 11, color: "#c2410c" }}>잔액이 부족합니다</span>
                  )}
                </div>
              </>
            )}
            {canEdit && isActive && (
              <Button variant="secondary" size="md" onClick={onPause} style={{}}>
                일시정지
              </Button>
            )}
            {!canEdit && (
              <span style={{ fontSize: 11, color: "#94a3b8" }}>VIEWER 권한으로 액션 불가</span>
            )}
          </>
        }
      />

      <div
        role="tablist"
        aria-label="캠페인 상세 탭"
        style={{
          display: "flex",
          gap: 4,
          borderBottom: "1px solid #e2e8f0",
          marginBottom: 20,
        }}
      >
        {(
          [
            ["overview", "개요"],
            ["logs", "발송 로그"],
          ] as Array<["overview" | "logs", string]>
        ).map(([k, l]) => {
          const active = tab === k;
          return (
            <button
              key={k}
              role="tab"
              aria-selected={active}
              tabIndex={active ? 0 : -1}
              onClick={() => setTab(k)}
              style={{
                position: "relative",
                padding: "10px 14px",
                marginBottom: -1,
                background: "transparent",
                border: 0,
                borderBottom: active
                  ? "2px solid var(--color-primary-strong, #3b5bdb)"
                  : "2px solid transparent",
                color: active ? "#1f2937" : "#64748b",
                fontSize: 13,
                fontWeight: active ? 700 : 500,
                fontFamily: "inherit",
                cursor: "pointer",
              }}
            >
              {l}
            </button>
          );
        })}
      </div>

      {tab === "logs" && (
        <NotificationLogTable
          projectId={projectId}
          campaignId={campaignId}
          campaignStatus={campaign.campaignStatus}
        />
      )}

      {tab === "overview" && (
      <>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 }}>
        <StatCard label="발송 수" value={sent.toLocaleString()} style={{}} />
        <StatCard
          label="배송"
          value={delivered.toLocaleString()}
          delta={sent > 0 ? `${((delivered / sent) * 100).toFixed(1)}%` : "—"}
          style={{}}
        />
        <StatCard
          label="클릭"
          value={clicked.toLocaleString()}
          delta={delivered > 0 ? `${((clicked / delivered) * 100).toFixed(1)}%` : "—"}
          trend="up"
          style={{}}
        />
        <StatCard
          label="실패"
          value={failed.toLocaleString()}
          delta={sent > 0 ? `${((failed / sent) * 100).toFixed(2)}%` : "—"}
          trend={failed > 0 ? "down" : "up"}
          style={{}}
        />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 340px", gap: 14 }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          <Card title="기본 정보" style={{}}>
            <DetailGrid
              rows={[
                ["이름", campaign.campaignName],
                ["유형", campaign.campaignType === "ONE_TIME" ? "일회성 (One-time)" : "자동화 (Automation)"],
                ["설명", campaign.campaignDescription],
                ["생성일시", fmtDateTime(campaign.createdAt)],
                ["수정일시", fmtDateTime(campaign.updatedAt)],
              ]}
            />
          </Card>

          <Card title="채널 · 세그먼트" style={{}}>
            <DetailGrid
              rows={[
                ["채널", CHANNEL_LABEL[campaign.channel]],
                [
                  "세그먼트",
                  campaign.segmentId == null
                    ? "전체 사용자 (세그먼트 미지정)"
                    : "세그먼트",
                ],
                ["세그먼트 빌드 상태", campaign.segmentBuildStatus ?? "—"],
              ]}
            />
          </Card>

          <Card title="메시지 템플릿" style={{}}>
            <DetailGrid
              rows={[
                ["템플릿 이름", template?.messageTemplateName ?? "(로드 중)"],
                ["채널", CHANNEL_LABEL[campaign.channel]],
                template
                  ? ["감지된 변수", template.requiredVariables.length > 0 ? template.requiredVariables.map((v) => `{{${v}}}`).join(" ") : "없음"]
                  : ["감지된 변수", "(로드 중)"],
              ]}
            />
          </Card>

          <Card title="발송 모드" style={{}}>
            <DetailGrid
              rows={[
                ["sendMode", campaign.sendMode ?? "—"],
              ]}
            />
            <div
              style={{
                marginTop: 8,
                padding: 8,
                fontSize: 11.5,
                color: "#94a3b8",
                background: "#f8fafc",
                borderRadius: 6,
                border: "1px dashed #e2e8f0",
              }}
            >
              schedule 세부 정보는 백엔드에서 별도 제공 예정 (D-1).
            </div>
          </Card>
        </div>

        <div style={{ position: "sticky", top: 16, alignSelf: "start" }}>
          {template ? (
            <MobilePreviewPane
              channel={campaign.channel}
              title={template.messageTemplateTitle ?? campaign.campaignName}
              body={template.messageTemplateBody}
            />
          ) : (
            <div
              style={{
                background: "#fff",
                border: "1px dashed #e2e8f0",
                borderRadius: 12,
                padding: 32,
                textAlign: "center",
                color: "#94a3b8",
                fontSize: 12,
              }}
            >
              템플릿 정보를 불러오는 중...
            </div>
          )}
        </div>
      </div>
      </>
      )}
    </div>
  );
}

function DetailGrid({ rows }: { rows: Array<[string, string]> }) {
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "140px 1fr",
        rowGap: 12,
        columnGap: 16,
        fontSize: 13,
      }}
    >
      {rows.map(([label, value], i) => (
        <div key={i} style={{ display: "contents" }}>
          <div style={{ color: "#64748b" }}>{label}</div>
          <div style={{ color: "#1f2937", wordBreak: "break-word" }}>{value}</div>
        </div>
      ))}
    </div>
  );
}
