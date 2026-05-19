import { useState } from "react";
import {
  useTemplateListQuery,
  useTemplateDetailQuery,
  useDeleteTemplateMutation,
} from "@/hooks/queries/template";
import { openModal, confirmDialog, toast } from "@/lib/ui-bus";
import { ApiError } from "@/api/types";
import { ChannelBadge, Tag } from "@/components/common";
import TemplateFormModal from "@/features/templates/TemplateFormModal";
import MobilePreviewPane from "../MobilePreviewPane";
import { useProjectStore } from "@/stores/project";
import type { MessageTemplateListItem } from "@/api/template";
import type { FieldErrorMap, WizardDraft } from "./wizardReducer";

export interface Step5TemplateProps {
  projectId: number;
  draft: WizardDraft;
  errors: FieldErrorMap;
  onSelect: (id: number | null, name: string | null) => void;
}

function fmtDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")} ${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export default function Step5Template({
  projectId,
  draft,
  errors,
  onSelect,
}: Step5TemplateProps) {
  const role = useProjectStore((s) => s.current?.role ?? "VIEWER");
  const canEdit = role !== "VIEWER";

  const [q, setQ] = useState("");
  const [viewId, setViewId] = useState<number | null>(
    draft.messageTemplateId ?? null,
  );
  const [isDeleting, setIsDeleting] = useState(false);

  const { data, isLoading, isError, refetch } = useTemplateListQuery(
    projectId,
    { page: 0, size: 30, channel: draft.channel, sort: "createdAt,desc" },
  );

  const detailQuery = useTemplateDetailQuery(projectId, viewId);
  const deleteMut = useDeleteTemplateMutation(projectId);

  const templates = (data?.messageTemplates ?? [])
    .filter((t) => t.channel === draft.channel)
    .filter((t) => {
      const ql = q.trim().toLowerCase();
      if (!ql) return true;
      return (
        (t.messageTemplateName ?? "").toLowerCase().includes(ql) ||
        (t.messageTitle ?? "").toLowerCase().includes(ql)
      );
    });

  const handleCardClick = (t: MessageTemplateListItem): void => {
    setViewId(t.messageTemplateId);
    onSelect(t.messageTemplateId, t.messageTemplateName);
  };

  const handleEdit = (): void => {
    if (!detailQuery.data || viewId == null) return;
    const d = detailQuery.data;
    openModal(({ close }) => (
      <TemplateFormModal
        mode="edit"
        projectId={projectId}
        templateId={viewId}
        segmentId={draft.segmentId}
        initialValues={{
          messageTemplateName: d.messageTemplateName,
          messageTemplateDescription: d.messageTemplateDescription,
          channel: d.channel,
          messageTemplateTitle: d.messageTemplateTitle,
          messageTemplateBody: d.messageTemplateBody,
          messageTemplateImageUrl: d.messageTemplateImageUrl ?? undefined,
        }}
        onClose={close}
      />
    ));
  };

  const handleDelete = async (): Promise<void> => {
    if (viewId == null) return;
    const ok = await confirmDialog({
      title: "템플릿을 삭제하시겠습니까?",
      message: "삭제된 템플릿은 복구할 수 없습니다.",
      confirmText: "삭제",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    setIsDeleting(true);
    try {
      await deleteMut.mutateAsync(viewId);
      toast("템플릿이 삭제되었습니다", "success");
      if (draft.messageTemplateId === viewId) {
        onSelect(null, null);
      }
      setViewId(null);
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : "삭제 중 오류가 발생했습니다";
      toast(msg, "error");
    } finally {
      setIsDeleting(false);
    }
  };

  const onCreateNew = (): void => {
    openModal(({ close }) => (
      <TemplateFormModal
        mode="create"
        projectId={projectId}
        segmentId={draft.segmentId}
        onClose={close}
      />
    ));
  };

  const viewedListItem = templates.find((t) => t.messageTemplateId === viewId);

  return (
    <div>
      <h2 style={titleStyle}>메시지 템플릿</h2>
      <p style={subtitleStyle}>
        선택한 채널 ({draft.channel}) 의 템플릿 중 발송할 메시지를 선택하세요.
      </p>

      {errors.messageTemplateId && (
        <div role="alert" style={errorBannerStyle}>
          {errors.messageTemplateId}
        </div>
      )}

      {/* 툴바 */}
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="템플릿 이름·본문 검색"
          style={searchInputStyle}
        />
        {canEdit && (
          <button type="button" onClick={onCreateNew} style={outlineBtnStyle}>
            + 새 템플릿
          </button>
        )}
      </div>

      {/* 메인 레이아웃: 왼쪽 목록 + 오른쪽 상세/미리보기 */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16, alignItems: "start" }}>

        {/* 왼쪽: 템플릿 목록 */}
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
            gap: 10,
            maxHeight: 460,
            overflowY: "auto",
            padding: 2,
          }}
        >
          {isLoading && (
            <div style={centerMsgStyle}>템플릿 목록을 불러오는 중...</div>
          )}

          {isError && (
            <div style={{ gridColumn: "1 / -1", ...errorBannerStyle }}>
              템플릿을 불러오지 못했습니다.{" "}
              <button
                type="button"
                onClick={() => refetch()}
                style={inlineLinkStyle}
              >
                재시도
              </button>
            </div>
          )}

          {!isLoading && !isError && templates.length === 0 && (
            <div style={centerMsgStyle}>
              {q
                ? "조건에 맞는 템플릿이 없습니다"
                : `${draft.channel} 채널 템플릿이 없습니다. [+ 새 템플릿] 으로 생성하세요.`}
            </div>
          )}

          {templates.map((t) => {
            const active = t.messageTemplateId === draft.messageTemplateId;
            const isViewing = t.messageTemplateId === viewId;

            return (
              <div
                key={t.messageTemplateId}
                role="button"
                tabIndex={0}
                onClick={() => handleCardClick(t)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === " ") {
                    e.preventDefault();
                    handleCardClick(t);
                  }
                }}
                style={{
                  padding: "10px 12px",
                  borderRadius: 10,
                  border: "1px solid",
                  borderColor: active ? "#3b5bdb" : isViewing ? "#94a3b8" : "#e2e8f0",
                  background: active
                    ? "rgba(59,91,219,0.06)"
                    : isViewing
                      ? "#f8fafc"
                      : "#fff",
                  cursor: "pointer",
                  textAlign: "left",
                  fontFamily: "inherit",
                  display: "flex",
                  flexDirection: "column",
                  gap: 6,
                  outline: "none",
                  transition: "border-color 120ms",
                }}
              >
                <div style={{ display: "flex", alignItems: "flex-start", gap: 8 }}>
                  <div style={{ minWidth: 0, flex: 1 }}>
                    <div
                      style={{
                        fontSize: 13,
                        fontWeight: 700,
                        color: active ? "#3b5bdb" : "#1f2937",
                        marginBottom: 4,
                        wordBreak: "break-word",
                      }}
                    >
                      {t.messageTemplateName}
                    </div>
                    <div
                      style={{
                        fontSize: 11.5,
                        color: "#64748b",
                        lineHeight: 1.45,
                        display: "-webkit-box",
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: "vertical",
                        overflow: "hidden",
                        minHeight: 30,
                      }}
                    >
                      {t.messageTitle}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* 오른쪽: 미리보기 + 상세 패널 */}
        <div style={{ position: "sticky", top: 16, maxHeight: "70vh", overflowY: "auto", display: "flex", flexDirection: "column", gap: 12 }}>
          {viewId != null ? (
            <>
              {/* 미리보기 */}
              <MobilePreviewPane
                channel={
                  detailQuery.data?.channel ?? viewedListItem?.channel ?? draft.channel
                }
                title={
                  detailQuery.data?.messageTemplateTitle ?? viewedListItem?.messageTitle
                }
                body={
                  detailQuery.data?.messageTemplateBody ?? viewedListItem?.messageTitle ?? ""
                }
              />

              {/* 상세 정보 */}
              {detailQuery.isLoading && (
                <div style={{ padding: 16, textAlign: "center", color: "#94a3b8", fontSize: 12 }}>
                  상세 정보를 불러오는 중...
                </div>
              )}

              {detailQuery.data && (
                <div
                  style={{
                    background: "#fff",
                    border: "1px solid #e2e8f0",
                    borderRadius: 12,
                    padding: 16,
                  }}
                >
                  {/* 이름 + 채널 */}
                  <div style={{ marginBottom: 10 }}>
                    <div
                      style={{
                        fontSize: 14,
                        fontWeight: 700,
                        color: "#1f2937",
                        marginBottom: 6,
                        wordBreak: "break-word",
                      }}
                    >
                      {detailQuery.data.messageTemplateName}
                    </div>
                    <ChannelBadge channel={detailQuery.data.channel} />
                  </div>

                  <DetailSection title="설명">
                    <p style={{ margin: 0, fontSize: 12, color: "#475569", lineHeight: 1.6 }}>
                      {detailQuery.data.messageTemplateDescription}
                    </p>
                  </DetailSection>

                  <DetailSection title="제목">
                    <p style={{ margin: 0, fontSize: 12, color: "#1f2937" }}>
                      {detailQuery.data.messageTemplateTitle}
                    </p>
                  </DetailSection>

                  <DetailSection title="메시지 본문">
                    <pre
                      style={{
                        margin: 0,
                        padding: 8,
                        background: "#f8fafc",
                        border: "1px solid #e2e8f0",
                        borderRadius: 6,
                        fontFamily: "var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)",
                        fontSize: 11,
                        lineHeight: 1.6,
                        color: "#1f2937",
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-word",
                        maxHeight: 120,
                        overflowY: "auto",
                      }}
                    >
                      {detailQuery.data.messageTemplateBody}
                    </pre>
                  </DetailSection>

                  <DetailSection title={`필수 변수 (${detailQuery.data.requiredVariables.length}개)`}>
                    {detailQuery.data.requiredVariables.length === 0 ? (
                      <span style={{ fontSize: 11, color: "#94a3b8" }}>감지된 변수가 없습니다.</span>
                    ) : (
                      <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
                        {detailQuery.data.requiredVariables.map((v) => (
                          <Tag key={v} tone="info" style={{}}>
                            {`{{${v}}}`}
                          </Tag>
                        ))}
                      </div>
                    )}
                  </DetailSection>

                  <DetailSection title="메타">
                    <DetailRow label="생성자" value={detailQuery.data.memberName} />
                    <DetailRow label="생성일" value={fmtDateTime(detailQuery.data.createdAt)} />
                    <DetailRow label="수정일" value={fmtDateTime(detailQuery.data.updatedAt)} />
                  </DetailSection>

                  {canEdit && (
                    <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 12, paddingTop: 12, borderTop: "1px solid #f1f5f9" }}>
                      <button
                        type="button"
                        onClick={handleDelete}
                        disabled={isDeleting}
                        style={dangerSmallBtnStyle}
                      >
                        {isDeleting ? "삭제 중..." : "삭제"}
                      </button>
                      <button
                        type="button"
                        onClick={handleEdit}
                        style={primaryBtnStyle}
                      >
                        수정
                      </button>
                    </div>
                  )}
                </div>
              )}
            </>
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
              왼쪽에서 템플릿을 선택하면 미리보기가 나타납니다.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function DetailSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 10 }}>
      <div
        style={{
          fontSize: 11,
          fontWeight: 600,
          color: "#475569",
          marginBottom: 6,
          paddingBottom: 4,
          borderBottom: "1px solid #f1f5f9",
        }}
      >
        {title}
      </div>
      {children}
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: "flex", padding: "3px 0", fontSize: 12 }}>
      <div style={{ width: 56, color: "#64748b", flexShrink: 0 }}>{label}</div>
      <div style={{ flex: 1, color: "#1f2937" }}>{value}</div>
    </div>
  );
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
  marginBottom: 16,
  fontSize: 13,
  color: "#64748b",
  lineHeight: 1.5,
};

const errorBannerStyle: React.CSSProperties = {
  marginBottom: 14,
  padding: 10,
  background: "rgba(239,68,68,.08)",
  border: "1px solid rgba(239,68,68,.25)",
  borderRadius: 8,
  color: "#b91c1c",
  fontSize: 12.5,
};

const searchInputStyle: React.CSSProperties = {
  flex: 1,
  height: 36,
  padding: "0 12px",
  fontFamily: "inherit",
  fontSize: 13,
  border: "1px solid #e2e8f0",
  borderRadius: 8,
  outline: "none",
  boxSizing: "border-box",
};

const outlineBtnStyle: React.CSSProperties = {
  height: 36,
  padding: "0 14px",
  fontSize: 12.5,
  fontWeight: 600,
  color: "#3b5bdb",
  background: "#fff",
  border: "1px solid #3b5bdb",
  borderRadius: 8,
  cursor: "pointer",
  fontFamily: "inherit",
  whiteSpace: "nowrap",
};

const primaryBtnStyle: React.CSSProperties = {
  height: 32,
  padding: "0 16px",
  fontSize: 12.5,
  fontWeight: 600,
  color: "#fff",
  background: "#3b5bdb",
  border: "none",
  borderRadius: 8,
  cursor: "pointer",
  fontFamily: "inherit",
};

const dangerSmallBtnStyle: React.CSSProperties = {
  height: 32,
  padding: "0 16px",
  fontSize: 12.5,
  fontWeight: 600,
  color: "#fff",
  background: "#ef4444",
  border: "1px solid #ef4444",
  borderRadius: 8,
  cursor: "pointer",
  fontFamily: "inherit",
};

const centerMsgStyle: React.CSSProperties = {
  gridColumn: "1 / -1",
  padding: 24,
  textAlign: "center",
  color: "#94a3b8",
  fontSize: 12,
};

const inlineLinkStyle: React.CSSProperties = {
  color: "#3b5bdb",
  background: "transparent",
  border: 0,
  cursor: "pointer",
  textDecoration: "underline",
  fontFamily: "inherit",
  fontSize: "inherit",
};
