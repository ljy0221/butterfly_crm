import { useState } from "react";
import { useTemplateListQuery } from "@/hooks/queries/template";
import { openModal } from "@/lib/ui-bus";
import TemplateFormModal from "@/features/templates/TemplateFormModal";
import MobilePreviewPane from "../MobilePreviewPane";
import type { MessageTemplateListItem } from "@/api/template";
import type { FieldErrorMap, WizardDraft } from "./wizardReducer";

/**
 * Step 5 — 템플릿 선택.
 * - Sprint 4 useTemplateListQuery({channel, size:12}) 재사용
 * - 카드 그리드 + 우측 MobilePreviewPane
 * - [+ 새 템플릿] → Sprint 4 TemplateFormModal 재사용
 */

export interface Step5TemplateProps {
  projectId: number;
  draft: WizardDraft;
  errors: FieldErrorMap;
  onSelect: (id: number, name: string) => void;
}

export default function Step5Template({
  projectId,
  draft,
  errors,
  onSelect,
}: Step5TemplateProps) {
  const [q, setQ] = useState("");
  const { data, isLoading, isError, refetch } = useTemplateListQuery(projectId, {
    page: 0,
    size: 30,
    channel: draft.channel,
    sort: "createdAt,desc",
  });

  // 백엔드는 채널 필터를 받지 않으므로 클라이언트에서 필터링.
  const templates = (data?.messageTemplates ?? [])
    .filter((t) => t.channel === draft.channel)
    .filter((t) => {
      const ql = q.trim().toLowerCase();
      if (!ql) return true;
      return (
        t.messageTemplateName.toLowerCase().includes(ql) ||
        (t.messageTitle ?? "").toLowerCase().includes(ql)
      );
    });

  const selected: MessageTemplateListItem | undefined = templates.find(
    (t) => t.messageTemplateId === draft.messageTemplateId,
  );

  const onCreateNew = (): void => {
    openModal(({ close }) => (
      <TemplateFormModal mode="create" projectId={projectId} onClose={close} />
    ));
  };

  return (
    <div>
      <h2 style={titleStyle}>메시지 템플릿</h2>
      <p style={subtitleStyle}>
        선택한 채널 ({draft.channel}) 의 템플릿 중 발송할 메시지를 선택하세요.
      </p>

      {errors.messageTemplateId && (
        <div role="alert" style={errorBanner}>
          {errors.messageTemplateId}
        </div>
      )}

      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 12 }}>
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="템플릿 이름·본문 검색"
          style={{
            flex: 1,
            height: 36,
            padding: "0 12px",
            fontFamily: "inherit",
            fontSize: 13,
            border: "1px solid #e2e8f0",
            borderRadius: 8,
            outline: "none",
            boxSizing: "border-box",
          }}
        />
        <button
          type="button"
          onClick={onCreateNew}
          style={{
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
          }}
        >
          + 새 템플릿
        </button>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 320px",
          gap: 16,
          alignItems: "start",
        }}
      >
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
            gap: 10,
            maxHeight: 460,
            overflowY: "auto",
            padding: 2,
          }}
        >
          {isLoading && (
            <div style={{ gridColumn: "1 / -1", padding: 24, textAlign: "center", color: "#94a3b8", fontSize: 12 }}>
              템플릿 목록을 불러오는 중...
            </div>
          )}

          {isError && (
            <div style={{ gridColumn: "1 / -1", ...errorBanner }}>
              템플릿을 불러오지 못했습니다.{" "}
              <button
                type="button"
                onClick={() => refetch()}
                style={{ color: "#3b5bdb", background: "transparent", border: 0, cursor: "pointer", textDecoration: "underline" }}
              >
                재시도
              </button>
            </div>
          )}

          {!isLoading && !isError && templates.length === 0 && (
            <div style={{ gridColumn: "1 / -1", padding: 24, textAlign: "center", color: "#94a3b8", fontSize: 12 }}>
              {q
                ? "조건에 맞는 템플릿이 없습니다"
                : `${draft.channel} 채널 템플릿이 없습니다. [+ 새 템플릿] 으로 생성하세요.`}
            </div>
          )}

          {templates.map((t) => {
            const active = t.messageTemplateId === draft.messageTemplateId;
            return (
              <button
                key={t.messageTemplateId}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => onSelect(t.messageTemplateId, t.messageTemplateName)}
                style={{
                  padding: 12,
                  borderRadius: 10,
                  border: "1px solid",
                  borderColor: active ? "#3b5bdb" : "#e2e8f0",
                  background: active ? "rgba(59,91,219,0.06)" : "#fff",
                  cursor: "pointer",
                  textAlign: "left",
                  fontFamily: "inherit",
                  display: "flex",
                  flexDirection: "column",
                  gap: 6,
                }}
              >
                <div style={{ fontSize: 13, fontWeight: 700, color: active ? "#3b5bdb" : "#1f2937" }}>
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
              </button>
            );
          })}
        </div>

        <div style={{ position: "sticky", top: 16 }}>
          {selected ? (
            <MobilePreviewPane
              channel={selected.channel}
              title={selected.messageTitle}
              body={selected.messageTitle}
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
              왼쪽에서 템플릿을 선택하면 미리보기가 나타납니다.
            </div>
          )}
        </div>
      </div>
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

const errorBanner: React.CSSProperties = {
  marginBottom: 14,
  padding: 10,
  background: "rgba(239,68,68,.08)",
  border: "1px solid rgba(239,68,68,.25)",
  borderRadius: 8,
  color: "#b91c1c",
  fontSize: 12.5,
};
