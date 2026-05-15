import { useMemo, useState } from "react";
import { Button, Tag } from "@/components/common";
import { ApiError } from "@/api/types";
import { toast } from "@/lib/ui-bus";
import {
  useCreateTemplateMutation,
  useUpdateTemplateMutation,
} from "@/hooks/queries/template";
import { useRecommendedVariablesQuery } from "@/hooks/queries/personalization";
import {
  createTemplateSchema,
  extractVariables,
  updateTemplateSchema,
  type Channel,
} from "@/schemas/template";
import VariableHighlight from "./VariableHighlight";

/**
 * Sprint 4 — 템플릿 생성/수정 공용 모달.
 *
 * S14P31B101-233 머지 후 실 API. 모든 필드(name/description/channel/title/body)는
 * 백엔드 record 의 @NotBlank 필드. imageUrl만 선택.
 */

export type TemplateFormMode = "create" | "edit";

export interface TemplateFormModalProps {
  mode: TemplateFormMode;
  projectId: number;
  /** mode="edit" 일 때만 사용. */
  templateId?: number;
  /** mode="edit" 일 때 prefill. mode="create" 일 때는 무시. */
  initialValues?: TemplateFormValues;
  /**
   * 추천 변수 산출 범위를 좁힐 때 사용. 캠페인 위저드처럼 세그먼트가 미리 선택된
   * 화면에서 모달을 띄울 때 전달한다. 미지정이면 프로젝트 전체 user_tags 빈도 기준.
   */
  segmentId?: number | null;
  onClose: () => void;
}

export interface TemplateFormValues {
  messageTemplateName: string;
  messageTemplateDescription: string;
  channel: Channel;
  messageTemplateTitle: string;
  messageTemplateBody: string;
  messageTemplateImageUrl?: string;
}

const CHANNEL_OPTIONS: Array<{ value: Channel; label: string }> = [
  { value: "PUSH", label: "PUSH (앱 푸시)" },
  { value: "EMAIL", label: "EMAIL (이메일)" },
  { value: "SMS", label: "SMS (문자)" },
  { value: "WEBHOOK", label: "WEBHOOK (외부 연동)" },
  { value: "KAKAO", label: "KAKAO (알림톡)" },
];

/**
 * API 응답이 없거나 빈 결과일 때 표시할 기본 변수. 프로젝트에 user_tags 가 아직
 * 적재되지 않은 초기 상태에서도 운영자가 형식을 익힐 수 있도록 남겨둔다.
 */
const FALLBACK_SUGGESTED_VARS = [
  "name",
  "product_name",
  "item_count",
  "coupon_code",
  "order_id",
];

interface FieldErrors {
  messageTemplateName?: string;
  messageTemplateDescription?: string;
  channel?: string;
  messageTemplateTitle?: string;
  messageTemplateBody?: string;
  messageTemplateImageUrl?: string;
  _root?: string;
}

export default function TemplateFormModal({
  mode,
  projectId,
  templateId,
  initialValues,
  segmentId,
  onClose,
}: TemplateFormModalProps) {
  const [name, setName] = useState<string>(
    initialValues?.messageTemplateName ?? "",
  );
  const [channel, setChannel] = useState<Channel>(
    initialValues?.channel ?? "SMS",
  );
  const [body, setBody] = useState<string>(
    initialValues?.messageTemplateBody ?? "",
  );
  const [description, setDescription] = useState<string>(
    initialValues?.messageTemplateDescription ?? "",
  );
  const [title, setTitle] = useState<string>(
    initialValues?.messageTemplateTitle ?? "",
  );
  const [imageUrl, setImageUrl] = useState<string>(
    initialValues?.messageTemplateImageUrl ?? "",
  );
  const [errors, setErrors] = useState<FieldErrors>({});

  const detectedVars = useMemo(() => extractVariables(body), [body]);

  const recommendedQuery = useRecommendedVariablesQuery(projectId, segmentId);
  const suggestedVars = useMemo<string[]>(() => {
    const apiVars = recommendedQuery.data?.variables.map((v) => v.key) ?? [];
    return apiVars.length > 0 ? apiVars : FALLBACK_SUGGESTED_VARS;
  }, [recommendedQuery.data]);

  const createMut = useCreateTemplateMutation(projectId);
  const updateMut = useUpdateTemplateMutation(projectId);
  const submitting = createMut.isPending || updateMut.isPending;

  const insertVariable = (varName: string): void => {
    const token = `{{${varName}}}`;
    setBody((prev) => (prev.length === 0 ? token : `${prev} ${token}`));
  };

  const validate = (): {
    ok: boolean;
    parsed?: TemplateFormValues;
    fe?: FieldErrors;
  } => {
    const schema = mode === "create" ? createTemplateSchema : updateTemplateSchema;
    const candidate = {
      messageTemplateName: name.trim(),
      messageTemplateDescription: description.trim(),
      channel,
      messageTemplateTitle: title.trim(),
      messageTemplateBody: body,
      messageTemplateImageUrl: imageUrl.trim() || undefined,
    };
    const parsed = schema.safeParse(candidate);
    if (parsed.success) {
      return { ok: true, parsed: parsed.data };
    }
    const fe: FieldErrors = {};
    for (const issue of parsed.error.issues) {
      const field = issue.path[0] as keyof FieldErrors | undefined;
      if (
        field === "messageTemplateName" ||
        field === "messageTemplateDescription" ||
        field === "channel" ||
        field === "messageTemplateTitle" ||
        field === "messageTemplateBody" ||
        field === "messageTemplateImageUrl"
      ) {
        fe[field] = issue.message;
      }
    }
    return { ok: false, fe };
  };

  const onSubmit = async (): Promise<void> => {
    const { ok, parsed, fe } = validate();
    if (!ok) {
      setErrors(fe ?? {});
      return;
    }
    setErrors({});

    try {
      if (mode === "create") {
        if (!parsed) return;
        await createMut.mutateAsync({ req: parsed });
        toast("템플릿이 생성되었습니다", "success");
      } else {
        if (!parsed || templateId == null) return;
        await updateMut.mutateAsync({ id: templateId, req: parsed });
        toast("템플릿이 수정되었습니다", "success");
      }
      onClose();
    } catch (err) {
      const msg =
        err instanceof ApiError
          ? err.message
          : err instanceof Error
            ? err.message
            : mode === "create"
              ? "템플릿 생성 중 오류가 발생했습니다"
              : "템플릿 수정 중 오류가 발생했습니다";
      setErrors({ _root: msg });
      toast(msg, "error");
    }
  };

  const headingId = "template-form-modal-title";
  const variablesHelpId = "template-form-detected-vars";

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby={headingId}
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
      <h2
        id={headingId}
        style={{
          margin: 0,
          marginBottom: 18,
          fontSize: 18,
          fontWeight: 700,
          color: "#1f2937",
          fontFamily: "var(--font-display)",
        }}
      >
        {mode === "create" ? "새 템플릿 만들기" : "템플릿 수정"}
      </h2>

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

      {/* 이름 */}
      <Field label="템플릿 이름" required error={errors.messageTemplateName}>
        <input
          autoFocus
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={255}
          placeholder="예: 환영 메시지"
          style={inputStyle(!!errors.messageTemplateName)}
        />
      </Field>

      {/* 설명 */}
      <Field
        label="설명"
        required
        error={errors.messageTemplateDescription}
        help={`${description.length}/500`}
      >
        <input
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={500}
          placeholder="템플릿 사용 시점/대상 등 메모"
          style={inputStyle(!!errors.messageTemplateDescription)}
        />
      </Field>

      {/* 채널 */}
      <Field label="채널" required error={errors.channel}>
        <select
          value={channel}
          onChange={(e) => setChannel(e.target.value as Channel)}
          style={{
            ...inputStyle(!!errors.channel),
            appearance: "none",
            cursor: "pointer",
          }}
        >
          {CHANNEL_OPTIONS.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </Field>

      {/* 제목 */}
      <Field
        label="제목"
        required
        error={errors.messageTemplateTitle}
        help={`${title.length}/255`}
      >
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={255}
          placeholder="알림 제목"
          style={inputStyle(!!errors.messageTemplateTitle)}
        />
      </Field>

      {/* 본문 + 변수 강조 */}
      <Field
        label="메시지 본문"
        required
        error={errors.messageTemplateBody}
        help={`${body.length}/10000자 · {{변수명}} 형식 사용 가능`}
      >
        <VariableHighlight
          value={body}
          onChange={setBody}
          rows={8}
          maxLength={10000}
          placeholder="안녕하세요 {{name}}님! ..."
          ariaLabel="메시지 본문"
          ariaDescribedBy={variablesHelpId}
          invalid={!!errors.messageTemplateBody}
        />
      </Field>

      {/* 이미지 URL (선택) */}
      <Field
        label="이미지 URL (선택)"
        error={errors.messageTemplateImageUrl}
        help={`${imageUrl.length}/500`}
      >
        <input
          value={imageUrl}
          onChange={(e) => setImageUrl(e.target.value)}
          maxLength={500}
          placeholder="https://..."
          style={inputStyle(!!errors.messageTemplateImageUrl)}
        />
      </Field>

      {/* 변수 미리보기 패널 */}
      <div
        id={variablesHelpId}
        style={{
          marginTop: -2,
          marginBottom: 14,
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
            alignItems: "center",
            marginBottom: 8,
          }}
        >
          <span style={{ fontSize: 12, fontWeight: 600, color: "#334155" }}>
            감지된 변수 {detectedVars.length}개
          </span>
          <span style={{ fontSize: 11, color: "#94a3b8" }}>클릭하여 본문에 삽입</span>
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
          {detectedVars.length === 0 && (
            <span style={{ fontSize: 12, color: "#94a3b8" }}>
              본문에 {`{{변수명}}`} 패턴이 없습니다.
            </span>
          )}
          {detectedVars.map((v) => (
            <Tag key={v} tone="info" style={{}}>
              {`{{${v}}}`}
            </Tag>
          ))}
        </div>
        <div
          style={{
            marginTop: 10,
            paddingTop: 10,
            borderTop: "1px dashed #e2e8f0",
            display: "flex",
            flexWrap: "wrap",
            gap: 4,
          }}
        >
          <span
            style={{
              fontSize: 11,
              color: "#94a3b8",
              alignSelf: "center",
              marginRight: 4,
            }}
          >
            {segmentId != null ? "세그먼트 추천 변수:" : "프로젝트 추천 변수:"}
          </span>
          {suggestedVars.map((v) => (
            <button
              key={v}
              type="button"
              onClick={() => insertVariable(v)}
              style={{
                fontSize: 11,
                fontFamily: "var(--font-mono)",
                padding: "3px 8px",
                border: "1px solid #e2e8f0",
                borderRadius: 4,
                background: "#fff",
                color: "#3b5bdb",
                cursor: "pointer",
                fontWeight: 600,
              }}
            >
              + {`{{${v}}}`}
            </button>
          ))}
        </div>
      </div>

      <div
        style={{
          display: "flex",
          justifyContent: "flex-end",
          gap: 8,
          marginTop: 4,
        }}
      >
        <Button variant="ghost" size="md" onClick={onClose} disabled={submitting} style={{}}>
          취소
        </Button>
        <Button
          variant="primaryStrong"
          size="md"
          onClick={onSubmit}
          disabled={submitting}
          style={{}}
        >
          {submitting
            ? "저장 중..."
            : mode === "create"
              ? "만들기"
              : "수정 저장"}
        </Button>
      </div>
    </div>
  );
}

function Field({
  label,
  required,
  error,
  help,
  children,
}: {
  label: string;
  required?: boolean;
  error?: string;
  help?: string;
  children: React.ReactNode;
}) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div
        style={{
          fontSize: 12,
          fontWeight: 500,
          color: "#334155",
          marginBottom: 6,
        }}
      >
        {label}
        {required && <span style={{ color: "#ef4444", marginLeft: 4 }}>*</span>}
      </div>
      {children}
      {(help || error) && (
        <div
          style={{
            marginTop: 6,
            fontSize: 11,
            color: error ? "#ef4444" : "#94a3b8",
            display: "flex",
            justifyContent: "space-between",
          }}
        >
          <span>{error ?? help}</span>
        </div>
      )}
    </div>
  );
}

function inputStyle(invalid: boolean): React.CSSProperties {
  return {
    width: "100%",
    height: 36,
    padding: "0 12px",
    fontFamily: "inherit",
    fontSize: 13,
    color: "#1f2937",
    background: "#fff",
    border: `1px solid ${invalid ? "#ef4444" : "#e2e8f0"}`,
    borderRadius: 8,
    outline: "none",
    boxSizing: "border-box",
  };
}
