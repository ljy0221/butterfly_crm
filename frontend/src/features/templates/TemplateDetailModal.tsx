import { Button, ChannelBadge, Tag } from "@/components/common";
import { ApiError } from "@/api/types";
import { confirmDialog, openModal, toast } from "@/lib/ui-bus";
import {
  useDeleteTemplateMutation,
  useTemplateDetailQuery,
} from "@/hooks/queries/template";
import { useProjectStore } from "@/stores/project";
import TemplateFormModal from "./TemplateFormModal";

/**
 * Sprint 4 — 템플릿 상세 모달.
 *
 * mock-fallback (GET /templates/{id} 미구현). DRAFT 일 때만 [수정] [삭제] 버튼 활성화.
 * EDITOR 미만(VIEWER) 은 두 버튼이 보이지 않는다.
 */

const STATUS_LABEL: Record<string, string> = {
  DRAFT: "초안",
  READY: "준비완료",
  ARCHIVED: "보관",
};

const STATUS_TONE: Record<string, string> = {
  DRAFT: "warning",
  READY: "success",
  ARCHIVED: "default",
};

export interface TemplateDetailModalProps {
  projectId: number;
  templateId: number;
  onClose: () => void;
}

function fmtDateTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const hh = String(d.getHours()).padStart(2, "0");
  const mi = String(d.getMinutes()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
}

export default function TemplateDetailModal({
  projectId,
  templateId,
  onClose,
}: TemplateDetailModalProps) {
  const role = useProjectStore((s) => s.current?.role ?? "VIEWER");
  const canEdit = role !== "VIEWER";

  const { data, isLoading, isError, refetch } = useTemplateDetailQuery(
    projectId,
    templateId,
  );

  const deleteMut = useDeleteTemplateMutation(projectId);

  const onClickEdit = (): void => {
    if (!data) return;
    onClose();
    openModal(({ close }) => (
      <TemplateFormModal
        mode="edit"
        projectId={projectId}
        templateId={data.messageTemplateId}
        initialValues={{
          messageTemplateName: data.messageTemplateName,
          channel: data.channel,
          messageTemplateBody: data.messageTemplateBody,
          description: data.description,
          title: data.title,
        }}
        onClose={close}
      />
    ));
  };

  const onClickDelete = async (): Promise<void> => {
    if (!data) return;
    const ok = await confirmDialog({
      title: "템플릿을 삭제하시겠습니까?",
      message: `"${data.messageTemplateName}" 템플릿이 영구 삭제됩니다.`,
      confirmText: "삭제",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;
    try {
      await deleteMut.mutateAsync(data.messageTemplateId);
      toast("템플릿이 삭제되었습니다", "success");
      onClose();
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

  const editable = !!data && data.status === "DRAFT";

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="template-detail-title"
      style={{
        background: "#fff",
        borderRadius: 14,
        padding: 24,
        width: 640,
        maxWidth: "92vw",
        maxHeight: "90vh",
        overflowY: "auto",
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          gap: 12,
          marginBottom: 16,
        }}
      >
        <div style={{ minWidth: 0, flex: 1 }}>
          <h2
            id="template-detail-title"
            style={{
              margin: 0,
              fontSize: 18,
              fontWeight: 700,
              color: "#1f2937",
              fontFamily: "var(--font-display)",
              wordBreak: "break-word",
            }}
          >
            {data?.messageTemplateName ?? "템플릿"}
          </h2>
          <div
            style={{
              marginTop: 4,
              display: "flex",
              alignItems: "center",
              gap: 8,
              flexWrap: "wrap",
            }}
          >
            {data && <ChannelBadge channel={data.channel} />}
            {data && (
              <Tag tone={STATUS_TONE[data.status] ?? "default"} dot style={{}}>
                {STATUS_LABEL[data.status] ?? data.status}
              </Tag>
            )}
          </div>
        </div>
      </div>

      {isLoading && (
        <div
          style={{
            padding: 32,
            textAlign: "center",
            color: "#64748b",
            fontSize: 13,
          }}
        >
          불러오는 중...
        </div>
      )}

      {isError && (
        <div
          role="alert"
          style={{
            padding: 16,
            background: "rgba(239,68,68,.06)",
            border: "1px solid rgba(239,68,68,.25)",
            borderRadius: 8,
            color: "#b91c1c",
            fontSize: 13,
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: 12,
          }}
        >
          <span>템플릿을 불러오지 못했습니다.</span>
          <Button variant="ghost" size="sm" onClick={() => refetch()} style={{}}>
            재시도
          </Button>
        </div>
      )}

      {data && (
        <>
          {data.description && (
            <Section title="설명">
              <p style={{ margin: 0, fontSize: 13, color: "#475569", lineHeight: 1.6 }}>
                {data.description}
              </p>
            </Section>
          )}

          {data.title && (
            <Section title="제목">
              <p style={{ margin: 0, fontSize: 13, color: "#1f2937" }}>{data.title}</p>
            </Section>
          )}

          <Section title="메시지 본문">
            <pre
              style={{
                margin: 0,
                padding: 12,
                background: "#f8fafc",
                border: "1px solid #e2e8f0",
                borderRadius: 8,
                fontFamily: "var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)",
                fontSize: 13,
                lineHeight: 1.6,
                color: "#1f2937",
                whiteSpace: "pre-wrap",
                wordBreak: "break-word",
              }}
            >
              {data.messageTemplateBody}
            </pre>
          </Section>

          <Section title={`필수 변수 (${data.requiredVariables.length}개)`}>
            {data.requiredVariables.length === 0 ? (
              <span style={{ fontSize: 12, color: "#94a3b8" }}>
                감지된 변수가 없습니다.
              </span>
            ) : (
              <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                {data.requiredVariables.map((v) => (
                  <Tag key={v} tone="info" style={{}}>
                    {`{{${v}}}`}
                  </Tag>
                ))}
              </div>
            )}
          </Section>

          <Section title="메타">
            <Row label="생성자" value={data.createdBy} />
            <Row label="생성일" value={fmtDateTime(data.createdAt)} />
            <Row label="수정일" value={fmtDateTime(data.updatedAt)} />
            <Row label="템플릿 ID" value={`#${data.messageTemplateId}`} mono />
          </Section>

          <div
            style={{
              display: "flex",
              justifyContent: "flex-end",
              gap: 8,
              marginTop: 16,
              paddingTop: 16,
              borderTop: "1px solid #f1f5f9",
            }}
          >
            <Button variant="ghost" size="md" onClick={onClose} style={{}}>
              닫기
            </Button>
            {canEdit && (
              <>
                <Button
                  variant="danger"
                  size="md"
                  onClick={onClickDelete}
                  disabled={!editable || deleteMut.isPending}
                  title={
                    !editable ? "DRAFT 상태의 템플릿만 삭제할 수 있습니다" : undefined
                  }
                  style={{}}
                >
                  {deleteMut.isPending ? "삭제 중..." : "삭제"}
                </Button>
                <Button
                  variant="primaryStrong"
                  size="md"
                  onClick={onClickEdit}
                  disabled={!editable}
                  title={
                    !editable ? "DRAFT 상태의 템플릿만 수정할 수 있습니다" : undefined
                  }
                  style={{}}
                >
                  수정
                </Button>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}

function Section({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div
        style={{
          fontSize: 12,
          fontWeight: 600,
          color: "#475569",
          marginBottom: 8,
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

function Row({
  label,
  value,
  mono,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div style={{ display: "flex", padding: "4px 0", fontSize: 13 }}>
      <div style={{ width: 96, color: "#64748b" }}>{label}</div>
      <div
        style={{
          flex: 1,
          color: "#1f2937",
          fontFamily: mono ? "var(--font-mono)" : undefined,
        }}
      >
        {value}
      </div>
    </div>
  );
}
