import { useEffect, useReducer, useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Card,
  Icon,
  PageHeader,
  Tag,
} from "@/components/common";
import { confirmDialog, openModal, toast } from "@/lib/ui-bus";
import { mapSegmentError } from "@/lib/segment-errors";
import { useProjectStore } from "@/stores/project";
import { fmtNum } from "@/utils/fmt.js";
import { createSegmentSchema } from "@/schemas/segment";
import { useSegmentDraftMutation } from "@/hooks/queries/ai";
import type { SegmentDraftResponse } from "@/api/ai";
import { useEventCategoriesQuery } from "@/hooks/queries/event";
import AiLaunchButton from "@/features/ai/AiLaunchButton";
import {
  useCreateSegmentMutation,
  useCancelSegmentBuildMutation,
  usePreviewSegmentMutation,
  useRebuildSegmentMutation,
  useSegmentConditionMetadataQuery,
  useSegmentDetailQuery,
  useUpdateSegmentMutation,
  useLinkedCampaignsQuery,
} from "@/hooks/queries/segment";
import type {
  BuildStatus,
  PreviewResponse,
  SegmentDetail,
} from "@/api/segment";
import ConditionTreeEditor from "./builder/ConditionTreeEditor";
import PreviewPanel from "./builder/PreviewPanel";
import { initialTree, treeReducer } from "./builder/treeReducer";
import BuildProgress from "./BuildProgress";

/**
 * Sprint 5 — SegmentBuilder.
 *
 * 단일 컴포넌트로 create / view / edit 3가지 모드를 처리.
 *  - mode="create": URL = /p/:projectId/segments/new
 *  - mode="view"  : URL = /p/:projectId/segments/:segmentId  (default)
 *  - editMode 토글: 우상단 [수정] 클릭 → editMode=true (URL 미변경)
 *
 * 핵심:
 *  - 트리 state 는 useReducer + path 기반 mutation (treeReducer)
 *  - 미리보기는 명시적 클릭만, previewCallCount >= 1 일 때만 저장 활성
 *  - dirty 추적 + beforeunload prompt + 페이지 이탈 confirm
 *  - 저장 성공 시 invalidate + (create) /segments/:id 로 navigate
 *
 * 권한:
 *  - VIEWER: 읽기 전용 (모든 폼 disabled, 미리보기는 EDITOR↑ 만)
 *  - EDITOR↑: 수정/저장/삭제/미리보기/재계산 가능
 */

export type SegmentBuilderMode = "create" | "view";

export interface SegmentBuilderProps {
  /** 라우트에 따라 결정 — /new 면 create, /:id 면 view (이후 editMode 토글). */
  mode: SegmentBuilderMode;
}

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

interface FieldErrors {
  name?: string;
  description?: string;
  conditionTree?: string;
  _root?: string;
}

export default function SegmentBuilder({ mode }: SegmentBuilderProps) {
  const project = useProjectStore((s) => s.current);
  const role = project?.role ?? "VIEWER";
  const canEdit = role !== "VIEWER";
  const projectId = project?.projectId ?? null;
  const navigate = useNavigate();
  const params = useParams<{ segmentId?: string }>();
  const urlSegmentId = params.segmentId ? Number(params.segmentId) : null;

  // mode="create" 인데 VIEWER → 목록으로 redirect
  if (mode === "create" && !canEdit) {
    return <Navigate to={`/p/${projectId}/segments`} replace />;
  }

  if (!projectId) {
    return <Navigate to="/projects" replace />;
  }

  if (mode === "view" && (urlSegmentId == null || Number.isNaN(urlSegmentId))) {
    return <Navigate to={`/p/${projectId}/segments`} replace />;
  }

  return (
    <BuilderInner
      key={`${mode}-${urlSegmentId ?? "new"}`}
      mode={mode}
      projectId={projectId}
      segmentId={urlSegmentId}
      canEdit={canEdit}
      onBack={() => navigate(`/p/${projectId}/segments`)}
      onCreated={(newId) => navigate(`/p/${projectId}/segments/${newId}`)}
    />
  );
}

interface BuilderInnerProps {
  mode: SegmentBuilderMode;
  projectId: number;
  segmentId: number | null;
  canEdit: boolean;
  onBack: () => void;
  onCreated: (newId: number) => void;
}

function BuilderInner({
  mode,
  projectId,
  segmentId,
  canEdit,
  onBack,
  onCreated,
}: BuilderInnerProps) {
  // ---------- Detail fetch (view/edit 시) ----------
  // PENDING/BUILDING 상태에서는 3초 폴링으로 READY/FAILED 전이를 빠르게 감지.
  const detailQuery = useSegmentDetailQuery(
    projectId,
    segmentId,
    mode === "view",
    {
      refetchInterval: (data) =>
        data?.buildStatus === "PENDING" || data?.buildStatus === "BUILDING"
          ? 3_000
          : false,
    },
  );
  const linkedCampaignsQuery = useLinkedCampaignsQuery(
    projectId,
    segmentId,
    mode === "view",
  );
  const conditionMetadataQuery = useSegmentConditionMetadataQuery(projectId);

  // ---------- mode + editMode ----------
  // create: 항상 edit 가능. view: 기본 readonly, [수정] 버튼으로 editMode 토글.
  const [editMode, setEditMode] = useState(false);
  const isFormDisabled = !canEdit || (mode === "view" && !editMode);

  // ---------- Form state ----------
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [tree, dispatch] = useReducer(treeReducer, undefined, initialTree);
  const [errors, setErrors] = useState<FieldErrors>({});

  // ---------- Preview state ----------
  const [previewResult, setPreviewResult] = useState<PreviewResponse | null>(null);
  const [previewCallCount, setPreviewCallCount] = useState(0);
  const [previewError, setPreviewError] = useState<unknown>(null);
  const [dirty, setDirty] = useState(false);
  const [dirtyAfterPreview, setDirtyAfterPreview] = useState(false);

  // ---------- Mutations ----------
  const createMut = useCreateSegmentMutation(projectId);
  const updateMut = useUpdateSegmentMutation(projectId);
  const previewMut = usePreviewSegmentMutation(projectId);
  const rebuildMut = useRebuildSegmentMutation(projectId);
  const cancelBuildMut = useCancelSegmentBuildMutation(projectId);
  const submitting = createMut.isPending || updateMut.isPending;

  // ---------- Detail → form 초기화 ----------
  useEffect(() => {
    if (mode !== "view") return;
    if (editMode) return;
    const detail = detailQuery.data;
    if (!detail) return;
    setName(detail.name);
    setDescription(detail.description ?? "");
    dispatch({ type: "SET_TREE", tree: detail.conditionTree });
    setDirty(false);
    setDirtyAfterPreview(false);
    // detail 의 estimatedCount 가 있으면 미리보기 결과로 표시 (preview 재실행 안 해도 저장 가능)
    if (detail.estimatedCount != null && detail.estimatedAt) {
      setPreviewResult({
        estimatedCount: detail.estimatedCount,
        method: "EXACT",
        estimatedAt: detail.estimatedAt,
      });
      setPreviewCallCount(1);
    }
  }, [mode, editMode, detailQuery.data]);

  // ---------- Form 변경 추적 (dirty) ----------
  useEffect(() => {
    // tree 변경 → dirty + dirtyAfterPreview
    setDirty(true);
    if (previewCallCount > 0) {
      setDirtyAfterPreview(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tree]);

  useEffect(() => {
    if (mode === "create") return;
    const detail = detailQuery.data;
    if (!detail) return;
    if (
      name !== detail.name ||
      (description ?? "") !== (detail.description ?? "")
    ) {
      setDirty(true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [name, description]);

  // ---------- beforeunload guard ----------
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent): string => {
      e.preventDefault();
      // 일부 브라우저에서 returnValue 필요
      e.returnValue = "";
      return "";
    };
    window.addEventListener("beforeunload", handler);
    return () => {
      window.removeEventListener("beforeunload", handler);
    };
  }, [dirty]);

  // ---------- 핸들러 ----------

  const isTreeNonEmpty = tree.conditions.length > 0;
  const canSave = isTreeNonEmpty && previewCallCount >= 1;

  const validateBeforeSave = (): {
    ok: boolean;
    fe?: FieldErrors;
  } => {
    const candidate = {
      name: name.trim(),
      description: description.trim(),
      conditionTree: tree,
    };
    const parsed = createSegmentSchema.safeParse(candidate);
    if (parsed.success) return { ok: true };
    const fe: FieldErrors = {};
    for (const issue of parsed.error.issues) {
      const top = issue.path[0];
      if (top === "name") fe.name = issue.message;
      else if (top === "description") fe.description = issue.message;
      else if (top === "conditionTree") fe.conditionTree = issue.message;
    }
    return { ok: false, fe };
  };

  const onRunPreview = async (): Promise<void> => {
    if (!isTreeNonEmpty) return;
    setPreviewError(null);
    try {
      const res = await previewMut.mutateAsync(tree);
      setPreviewResult(res);
      setPreviewCallCount((n) => n + 1);
      setDirtyAfterPreview(false);
    } catch (err) {
      setPreviewError(err);
      setPreviewResult(null);
    }
  };

  const openSegmentCopilot = (): void => {
    openModal(({ close }) => (
      <NaturalSegmentDraftModal
        projectId={projectId}
        name={name}
        description={description}
        tree={tree}
        onApply={onApplySegmentDraft}
        onClose={close}
      />
    ));
  };

  const onApplySegmentDraft = (draft: SegmentDraftResponse): void => {
    if (draft.name) {
      setName(draft.name.slice(0, 30));
    }
    if (draft.description) {
      setDescription(draft.description.slice(0, 100));
    }
    dispatch({ type: "SET_TREE", tree: draft.conditionTree });
    setPreviewResult(null);
    setPreviewError(null);
    setPreviewCallCount(0);
    setDirtyAfterPreview(false);
    setDirty(true);
    setErrors({});
    toast("AI 초안을 조건 트리에 적용했습니다. 저장 전 미리보기를 실행해 주세요.", "success");
  };

  const onSave = async (): Promise<void> => {
    const { ok, fe } = validateBeforeSave();
    if (!ok) {
      setErrors(fe ?? {});
      toast("입력 값을 확인해주세요", "warning");
      return;
    }
    setErrors({});

    try {
      if (mode === "create") {
        const created = await createMut.mutateAsync({
          name: name.trim(),
          description: description.trim(),
          conditionTree: tree,
        });
        toast("세그먼트가 생성되었습니다", "success");
        setDirty(false);
        onCreated(created.segmentId);
      } else {
        if (segmentId == null) return;
        await updateMut.mutateAsync({
          segmentId,
          name: name.trim(),
          description: description.trim(),
          conditionTree: tree,
        });
        toast("세그먼트가 수정되었습니다", "success");
        setDirty(false);
        setDirtyAfterPreview(false);
        setEditMode(false);
      }
    } catch (err) {
      const m = mapSegmentError(err);
      const next: FieldErrors = {};
      if (m.field === "name") next.name = m.message;
      else if (m.field === "description") next.description = m.message;
      else if (m.field === "conditionTree") next.conditionTree = m.message;
      else next._root = m.message;
      setErrors(next);
      toast(m.message, "error");
    }
  };

  const onCancel = async (): Promise<void> => {
    if (dirty) {
      const ok = await confirmDialog({
        title: "변경사항을 취소하시겠습니까?",
        message: "저장되지 않은 변경사항이 사라집니다.",
        confirmText: "취소하기",
        cancelText: "계속 편집",
        tone: "danger",
      });
      if (!ok) return;
    }
    if (mode === "create") {
      onBack();
    } else {
      // view 모드로 복귀 → form reset (detail 다시 적용)
      const detail = detailQuery.data;
      if (detail) {
        setName(detail.name);
        setDescription(detail.description ?? "");
        dispatch({ type: "SET_TREE", tree: detail.conditionTree });
      }
      setDirty(false);
      setDirtyAfterPreview(false);
      setEditMode(false);
      setErrors({});
    }
  };

  const onEnterEdit = (): void => {
    setEditMode(true);
  };

  const onRebuild = async (): Promise<void> => {
    if (segmentId == null) return;
    try {
      await rebuildMut.mutateAsync(segmentId);
      toast("재계산 요청을 큐에 넣었습니다. 완료까지 잠시 기다려 주세요.", "info");
    } catch (err) {
      const m = mapSegmentError(err);
      toast(m.message, "error");
    }
  };

  const onCancelBuild = async (): Promise<void> => {
    if (segmentId == null) return;
    const ok = await confirmDialog({
      title: "빌드를 취소하시겠습니까?",
      message: "현재 대기/진행 중인 빌드를 실패 상태로 바꿉니다. 이후 재계산을 다시 요청할 수 있습니다.",
      confirmText: "빌드 취소",
      cancelText: "닫기",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await cancelBuildMut.mutateAsync(segmentId);
      toast("빌드를 취소했습니다. 필요하면 재계산을 다시 실행해 주세요.", "success");
    } catch (err) {
      const m = mapSegmentError(err);
      toast(m.message, "error");
    }
  };

  // ---------- 렌더 ----------

  if (mode === "view" && detailQuery.isLoading) {
    return (
      <div>
        <PageHeader
          back="세그먼트"
          onBack={onBack}
          title="세그먼트 상세"
          subtitle="불러오는 중..."
          style={{}}
        />
        <Card padding={48} style={{ textAlign: "center", color: "#94a3b8" }}>
          <span>로딩 중...</span>
        </Card>
      </div>
    );
  }

  if (mode === "view" && detailQuery.isError) {
    const err = detailQuery.error;
    const m = err ? mapSegmentError(err) : { message: "조회 실패" };
    return (
      <div>
        <PageHeader
          back="세그먼트"
          onBack={onBack}
          title="세그먼트 상세"
          style={{}}
        />
        <Card padding={48} style={{ textAlign: "center" }}>
          <div style={{ color: "#b91c1c", fontSize: 13, marginBottom: 14 }}>
            {m.message}
          </div>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => detailQuery.refetch()}
            style={{}}
          >
            재시도
          </Button>
        </Card>
      </div>
    );
  }

  const detail: SegmentDetail | undefined = detailQuery.data;

  // 저장 비활성 가드: 진행 중 캠페인 있는 경우 toast로 안내
  const lockedByActiveCampaign =
    mode === "view" &&
    (linkedCampaignsQuery.data?.active.length ?? 0) > 0;

  return (
    <div>
      <PageHeader
        back="세그먼트"
        onBack={() => {
          if (dirty) {
            void onBack();
          } else {
            onBack();
          }
        }}
        title={mode === "create" ? "새 세그먼트" : detail?.name ?? "세그먼트"}
        subtitle={
          mode === "create"
            ? "조건을 조합해 타겟을 정의하고 미리보기로 모수를 확인하세요."
            : editMode
              ? "수정 중 — 미리보기 후 저장 가능합니다."
              : "조건 트리와 빌드 상태를 확인합니다."
        }
        actions={
          <>
            {mode === "view" && !editMode && canEdit && (
              <Button
                variant="primaryStrong"
                size="md"
                icon={Icon.edit(12)}
                onClick={onEnterEdit}
                disabled={
                  detail?.buildStatus === "PENDING" ||
                  detail?.buildStatus === "BUILDING"
                }
                style={{}}
              >
                수정
              </Button>
            )}
            {(mode === "create" || editMode) && (
              <>
                <Button
                  variant="ghost"
                  size="md"
                  onClick={onCancel}
                  disabled={submitting}
                  style={{}}
                >
                  취소
                </Button>
                <Button
                  variant="primaryStrong"
                  size="md"
                  icon={Icon.check(12)}
                  onClick={onSave}
                  disabled={submitting || !canSave}
                  style={{
                    opacity: !canSave || submitting ? 0.5 : 1,
                  }}
                  title={
                    !isTreeNonEmpty
                      ? "조건을 1개 이상 추가하세요"
                      : previewCallCount < 1
                        ? "미리보기를 1회 이상 실행하세요"
                        : undefined
                  }
                >
                  {submitting ? "저장 중..." : "저장"}
                </Button>
              </>
            )}
          </>
        }
        style={{}}
      />

      {/* Banner: 진행 중 캠페인이 있으면 수정 차단 안내 */}
      {lockedByActiveCampaign && (
        <div
          role="status"
          style={{
            marginBottom: 14,
            padding: 12,
            background: "rgba(245,158,11,.10)",
            border: "1px solid rgba(245,158,11,.25)",
            borderRadius: 8,
            color: "#b45309",
            fontSize: 12.5,
          }}
        >
          진행 중인 캠페인 {linkedCampaignsQuery.data?.active.length}개에 연결되어
          있어 수정/삭제 시 백엔드에서 차단될 수 있습니다.
        </div>
      )}

      {errors._root && (
        <div
          role="alert"
          style={{
            marginBottom: 14,
            padding: 10,
            background: "rgba(239,68,68,.08)",
            border: "1px solid rgba(239,68,68,.25)",
            borderRadius: 8,
            color: "#b91c1c",
            fontSize: 12.5,
          }}
        >
          {errors._root}
        </div>
      )}

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(0, 2fr) minmax(280px, 1fr)",
          gap: 14,
          alignItems: "flex-start",
        }}
      >
        {/* LEFT */}
        <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
          {/* 기본 정보 */}
          <Card style={{}}>
            <div
              style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}
            >
              <div>
                <div style={fieldLabelStyle}>
                  세그먼트 이름{" "}
                  <span style={{ color: "#ef4444" }}>*</span>
                </div>
                <input
                  value={name}
                  disabled={isFormDisabled}
                  onChange={(e) => setName(e.target.value)}
                  maxLength={30}
                  placeholder="예: VIP 재구매 후보"
                  style={{
                    ...textInputStyle,
                    fontSize: 15,
                    fontWeight: 600,
                    height: 40,
                    background: isFormDisabled ? "#f8fafc" : "#fff",
                    borderColor: errors.name ? "#ef4444" : "#e2e8f0",
                  }}
                />
                <div
                  style={{
                    marginTop: 6,
                    fontSize: 11,
                    color: errors.name ? "#ef4444" : "#94a3b8",
                  }}
                >
                  {errors.name ?? `${name.length}/30 · 프로젝트 내 중복 불가`}
                </div>
              </div>
              <div>
                <div style={fieldLabelStyle}>설명</div>
                <input
                  value={description}
                  disabled={isFormDisabled}
                  onChange={(e) => setDescription(e.target.value)}
                  maxLength={100}
                  placeholder="세그먼트가 대상으로 하는 사용자를 설명하세요"
                  style={{
                    ...textInputStyle,
                    height: 40,
                    background: isFormDisabled ? "#f8fafc" : "#fff",
                    borderColor: errors.description ? "#ef4444" : "#e2e8f0",
                  }}
                />
                <div
                  style={{
                    marginTop: 6,
                    fontSize: 11,
                    color: errors.description ? "#ef4444" : "#94a3b8",
                  }}
                >
                  {errors.description ?? `${description.length}/100`}
                </div>
              </div>
            </div>
          </Card>

          <Card
            title="조건 구성"
            subtitle="이벤트·사용자·태그·디바이스 조건을 조합합니다 (AND/OR · 최대 3단계 중첩)"
            action={
              !isFormDisabled ? (
                <AiLaunchButton
                  label="AI로 조건 만들기"
                  caption="Segment Copilot"
                  onClick={openSegmentCopilot}
                />
              ) : undefined
            }
            style={{}}
          >
            <ConditionTreeEditor
              node={tree}
              path={[]}
              dispatch={dispatch}
              userAttributes={conditionMetadataQuery.data?.userAttributes ?? []}
              disabled={isFormDisabled}
              rootDepth={0}
            />
            {errors.conditionTree && (
              <div
                style={{
                  marginTop: 10,
                  fontSize: 11,
                  color: "#ef4444",
                }}
              >
                {errors.conditionTree}
              </div>
            )}
          </Card>
        </div>

        {/* RIGHT (sticky) */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 14,
            position: "sticky",
            top: 16,
          }}
        >
          <PreviewPanel
            preview={previewResult}
            isLoading={previewMut.isPending}
            error={previewError}
            dirtyAfterPreview={dirtyAfterPreview}
            canPreview={canEdit && isTreeNonEmpty}
            disabled={!canEdit}
            onRunPreview={onRunPreview}
          />

          {mode === "view" && detail && (
            <Card title="빌드 상태" style={{}}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  marginBottom: 10,
                }}
              >
                <Tag tone={BUILD_STATUS_TONE[detail.buildStatus]} dot style={{}}>
                  {BUILD_STATUS_LABEL[detail.buildStatus]}
                </Tag>
                {detail.estimatedCount != null && (
                  <span
                    style={{
                      fontSize: 12,
                      color: "#64748b",
                      fontVariantNumeric: "tabular-nums",
                    }}
                  >
                    저장 모수 {fmtNum(detail.estimatedCount)}
                  </span>
                )}
              </div>
              {detail.audienceStats && (
                <AudienceStatsBlock stats={detail.audienceStats} />
              )}
              {/* 재빌드는 DELETE 후 INSERT SELECT 단일 statement로 적재되어 select/insert 단계별
                  진행률을 알 수 없다. 기존 segment_members 때문에 100%로 오해되지 않도록
                  불확정 진행 표시만 노출한다. */}
              {(detail.buildStatus === "BUILDING" || detail.buildStatus === "PENDING") && (
                <BuildProgress
                  current={0}
                  target={null}
                  startedAt={detail.buildStartedAt}
                />
              )}
              <div style={{ fontSize: 11, color: "#94a3b8", marginBottom: 12 }}>
                {detail.lastBuiltAt
                  ? `최종 빌드: ${detail.lastBuiltAt}`
                  : "빌드 이력 없음"}
              </div>
              {canEdit && (
                <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                  {(detail.buildStatus === "PENDING" ||
                    detail.buildStatus === "BUILDING") && (
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={onCancelBuild}
                      disabled={cancelBuildMut.isPending}
                      style={{
                        width: "100%",
                        justifyContent: "center",
                      }}
                    >
                      {cancelBuildMut.isPending ? "취소 중..." : "빌드 취소"}
                    </Button>
                  )}
                  {detail.buildStatus !== "PENDING" &&
                    detail.buildStatus !== "BUILDING" && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={onRebuild}
                      disabled={rebuildMut.isPending || cancelBuildMut.isPending}
                      style={{
                        width: "100%",
                        justifyContent: "center",
                      }}
                    >
                      {rebuildMut.isPending ? "요청 중..." : "재계산"}
                    </Button>
                  )}
                </div>
              )}
            </Card>
          )}

          {mode === "view" && (
            <Card
              title="매칭 사용자"
              subtitle="조건에 매칭되는 실제 사용자 목록"
              style={{}}
            >
              <div
                style={{
                  padding: "20px 0",
                  textAlign: "center",
                  color: "#94a3b8",
                  fontSize: 12,
                }}
              >
                <div style={{ marginBottom: 6 }}>Phase 8 출시 예정</div>
                <Tag tone="default" style={{}}>
                  segment_users (mock)
                </Tag>
              </div>
            </Card>
          )}

          {mode === "view" && (
            <Card title="연결된 캠페인" style={{}}>
              <LinkedCampaignsView
                isLoading={linkedCampaignsQuery.isLoading}
                data={linkedCampaignsQuery.data}
              />
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}

function NaturalSegmentDraftModal({
  projectId,
  name,
  description,
  tree,
  onApply,
  onClose,
}: {
  projectId: number;
  name: string;
  description: string;
  tree: SegmentDraftResponse["conditionTree"];
  onApply: (draft: SegmentDraftResponse) => void;
  onClose: () => void;
}) {
  const [prompt, setPrompt] = useState("");
  const [draft, setDraft] = useState<SegmentDraftResponse | null>(null);
  const segmentDraftMut = useSegmentDraftMutation(projectId);
  const eventCategoriesQuery = useEventCategoriesQuery(projectId);

  const onGenerate = async (): Promise<void> => {
    const nextPrompt = prompt.trim();
    if (!nextPrompt) {
      toast("세그먼트 조건을 자연어로 입력해 주세요.", "warning");
      return;
    }
    try {
      const res = await segmentDraftMut.mutateAsync({
        prompt: nextPrompt,
        availableEvents: (eventCategoriesQuery.data ?? []).map((event) => ({
          categoryId: event.categoryId,
          categoryName: event.categoryName,
          displayName: event.displayName,
          description: event.description,
        })),
        context: {
          name,
          description,
          currentConditionTree: tree,
        },
      });
      setDraft(res);
      toast("AI 세그먼트 조건 초안을 만들었습니다. 적용 전 내용을 확인해 주세요.", "success");
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : "AI 세그먼트 조건 초안을 만들지 못했습니다.";
      toast(msg, "error");
    }
  };
  return (
    <section
      aria-label="세그먼트 AI 초안"
      style={{
        width: "min(760px, calc(100vw - 48px))",
        background: "#fff",
        border: "1px solid rgba(99,102,241,.24)",
        borderRadius: 16,
        padding: 20,
        boxShadow: "0 28px 80px -24px rgba(15,23,42,.42)",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          gap: 16,
          alignItems: "flex-start",
        }}
      >
        <div>
          <div style={{ fontSize: 16, fontWeight: 800, color: "#111827" }}>
            AI Segment Copilot
          </div>
          <p style={{ margin: "5px 0 0", fontSize: 12, color: "#64748b" }}>
            대상 고객을 문장으로 입력하면 조건 트리 초안을 생성합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="닫기"
          style={{
            width: 30,
            height: 30,
            border: "1px solid #e2e8f0",
            borderRadius: 8,
            background: "#fff",
            color: "#64748b",
            cursor: "pointer",
            fontSize: 18,
            lineHeight: 1,
          }}
        >
          ×
        </button>
      </div>
      <div style={{ marginTop: 16, display: "flex", justifyContent: "flex-end" }}>
        <Button
          variant="primaryStrong"
          size="sm"
          icon={Icon.send(12)}
          onClick={onGenerate}
          disabled={segmentDraftMut.isPending}
          style={{}}
        >
          {segmentDraftMut.isPending ? "생성 중..." : "초안 생성"}
        </Button>
      </div>
      <textarea
        value={prompt}
        onChange={(e) => setPrompt(e.target.value)}
        rows={4}
        placeholder="예: 최근 30일 장바구니 이벤트가 1회 이상이고 구매 이벤트가 없는 iOS 사용자"
        style={{
          width: "100%",
          marginTop: 12,
          minHeight: 72,
          resize: "vertical",
          padding: 12,
          fontFamily: "inherit",
          fontSize: 13,
          lineHeight: 1.5,
          color: "#1f2937",
          border: "1px solid #cbd5e1",
          borderRadius: 8,
          outline: "none",
          boxSizing: "border-box",
        }}
      />

      {draft && (
        <div
          style={{
            marginTop: 12,
            padding: 12,
            background: "#f8fafc",
            border: "1px solid #e2e8f0",
            borderRadius: 8,
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              gap: 10,
              alignItems: "center",
            }}
          >
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 13, fontWeight: 700, color: "#1f2937" }}>
                {draft.name ?? "세그먼트 조건 초안"}
              </div>
              <div style={{ marginTop: 4, fontSize: 12, color: "#64748b" }}>
                조건 {countConditions(draft.conditionTree)}개
                {draft.description ? ` · ${draft.description}` : ""}
              </div>
            </div>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => onApply(draft)}
              style={{}}
            >
              조건 트리에 적용
            </Button>
          </div>
          {draft.notes && draft.notes.length > 0 && (
            <div
              style={{
                marginTop: 8,
                fontSize: 11,
                color: "#64748b",
                lineHeight: 1.5,
              }}
            >
              {draft.notes.slice(0, 2).join(" · ")}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function countConditions(node: SegmentDraftResponse["conditionTree"]): number {
  let count = 0;
  for (const child of node.conditions) {
    if (child.type === "group") {
      count += countConditions(child);
    } else {
      count += 1;
    }
  }
  return count;
}

function AudienceStatsBlock({
  stats,
}: {
  stats: NonNullable<SegmentDetail["audienceStats"]>;
}) {
  const rows = [
    {
      label: "대상자",
      value: stats.totalCount,
      help: "현재 세그먼트에 추출된 전체 사용자",
    },
    {
      label: "수신동의",
      value: stats.pushOptInCount,
      help: "푸시 수신 동의가 켜진 사용자",
    },
    {
      label: "발송가능",
      value: stats.pushSendableCount,
      help: "수신동의와 유효 디바이스 토큰을 모두 가진 사용자",
    },
  ];

  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
        gap: 8,
        marginBottom: 12,
      }}
    >
      {rows.map((row) => (
        <div
          key={row.label}
          title={row.help}
          style={{
            minWidth: 0,
            padding: "9px 10px",
            border: "1px solid #e2e8f0",
            borderRadius: 8,
            background: "#f8fafc",
          }}
        >
          <div
            style={{
              marginBottom: 3,
              fontSize: 10.5,
              fontWeight: 700,
              color: "#64748b",
            }}
          >
            {row.label}
          </div>
          <div
            style={{
              fontSize: 13,
              fontWeight: 700,
              color: "#1f2937",
              fontVariantNumeric: "tabular-nums",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
          >
            {fmtNum(row.value)}
          </div>
        </div>
      ))}
    </div>
  );
}

function LinkedCampaignsView({
  isLoading,
  data,
}: {
  isLoading: boolean;
  data: ReturnType<typeof useLinkedCampaignsQuery>["data"];
}) {
  if (isLoading) {
    return (
      <div style={{ fontSize: 12, color: "#94a3b8", padding: "8px 0" }}>
        불러오는 중...
      </div>
    );
  }
  if (!data || data.totalCount === 0) {
    return (
      <div
        style={{
          padding: "12px 0",
          textAlign: "center",
          color: "#94a3b8",
          fontSize: 12,
        }}
      >
        <div style={{ marginBottom: 6 }}>연결된 캠페인이 없습니다</div>
        <Tag tone="default" style={{}}>
          Sprint 6 mock store 연동 예정
        </Tag>
      </div>
    );
  }
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      {data.active.map((c) => (
        <div
          key={c.campaignId}
          style={{
            display: "flex",
            alignItems: "center",
            gap: 8,
            fontSize: 12,
          }}
        >
          <span
            style={{
              width: 6,
              height: 6,
              borderRadius: 999,
              background: "#16a34a",
            }}
          />
          <span style={{ flex: 1, color: "#1f2937" }}>{c.name}</span>
          <Tag tone="success" dot style={{}}>
            {c.campaignStatus}
          </Tag>
        </div>
      ))}
    </div>
  );
}

const fieldLabelStyle: React.CSSProperties = {
  fontSize: 11,
  fontWeight: 600,
  color: "#64748b",
  marginBottom: 6,
  letterSpacing: "-0.005em",
};

const textInputStyle: React.CSSProperties = {
  width: "100%",
  padding: "0 14px",
  fontFamily: "inherit",
  fontSize: 13,
  color: "#1f2937",
  background: "#fff",
  border: "1px solid #e2e8f0",
  borderRadius: 8,
  outline: "none",
  boxSizing: "border-box",
};
