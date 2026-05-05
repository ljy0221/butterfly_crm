import { useState } from "react";
import { Button, Checkbox, Icon, Input } from "@/components/common";
import { toast } from "@/lib/ui-bus";
import { mapEventError } from "@/lib/event-errors";
import { useCreateCategoryMutation } from "@/hooks/queries/event";
import {
  categoryNameSchema,
  displayNameSchema,
  PropertyTypeSchema,
  propertyKeySchema,
  type PropertyType,
} from "@/schemas/event";
import type { PropertyRecord } from "@/api/event";

/**
 * Sprint 7 — 이벤트 카테고리 추가 모달.
 *
 * - 이름/표시명/설명 + 속성 행 (key/type/required) 동적 추가/삭제
 * - zod 스키마로 인라인 검증 (categoryNameSchema / propertyKeySchema)
 * - mutation 성공 시 toast + close
 */

interface Props {
  projectId: number;
  close: () => void;
}

interface PropRow {
  key: string;
  type: PropertyType;
  required: boolean;
  example: string;
}

const TYPE_OPTIONS: PropertyType[] = [
  "string",
  "number",
  "boolean",
  "datetime",
  "object",
];

export default function AddEventCategoryModal({ projectId, close }: Props) {
  const [categoryName, setCategoryName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [props, setProps] = useState<PropRow[]>([
    { key: "", type: "string", required: false, example: "" },
  ]);
  const [submitting, setSubmitting] = useState(false);

  const createMut = useCreateCategoryMutation(projectId);

  const categoryNameError = (() => {
    if (!categoryName) return null;
    const r = categoryNameSchema.safeParse(categoryName);
    return r.success ? null : r.error.issues[0]?.message ?? "잘못된 이름";
  })();
  const displayNameError = (() => {
    if (!displayName) return null;
    const r = displayNameSchema.safeParse(displayName);
    return r.success ? null : r.error.issues[0]?.message ?? "잘못된 표시명";
  })();

  const addRow = (): void =>
    setProps((p) => [
      ...p,
      { key: "", type: "string", required: false, example: "" },
    ]);
  const rmRow = (i: number): void =>
    setProps((p) => p.filter((_, j) => j !== i));
  const updateRow = (i: number, patch: Partial<PropRow>): void =>
    setProps((p) => p.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  const submit = async (): Promise<void> => {
    if (!categoryNameSchema.safeParse(categoryName).success) {
      toast("이벤트명을 확인해주세요", "error");
      return;
    }
    if (!displayNameSchema.safeParse(displayName).success) {
      toast("표시명을 확인해주세요", "error");
      return;
    }
    const cleaned: PropertyRecord[] = [];
    const seen = new Set<string>();
    for (const p of props) {
      if (!p.key.trim()) continue;
      const r = propertyKeySchema.safeParse(p.key);
      if (!r.success) {
        toast(`속성 키 형식 오류: ${p.key}`, "error");
        return;
      }
      if (seen.has(p.key)) {
        toast(`속성 키 중복: ${p.key}`, "error");
        return;
      }
      if (!PropertyTypeSchema.safeParse(p.type).success) {
        toast(`속성 타입 오류: ${p.type}`, "error");
        return;
      }
      seen.add(p.key);
      cleaned.push({
        key: p.key,
        type: p.type,
        required: p.required,
        example: p.example.trim() || undefined,
      });
    }

    setSubmitting(true);
    try {
      await createMut.mutateAsync({
        categoryName,
        displayName,
        description: description.trim() || undefined,
        properties: cleaned,
      });
      toast(`이벤트 '${categoryName}' 카테고리를 추가했습니다`, "success");
      close();
    } catch (err) {
      const m = mapEventError(err);
      toast(m.message, "error");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        width: 600,
        maxWidth: "92vw",
        background: "#fff",
        borderRadius: 14,
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
      }}
    >
      <div
        style={{
          padding: "18px 22px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div>
          <div
            style={{
              fontSize: 17,
              fontWeight: 700,
              color: "#1f2937",
              fontFamily: "var(--font-display)",
            }}
          >
            이벤트 카테고리 추가
          </div>
          <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>
            등록된 이벤트만 SDK 가 수집할 수 있습니다.
          </div>
        </div>
        <button
          type="button"
          onClick={close}
          aria-label="닫기"
          style={{
            border: 0,
            background: "transparent",
            color: "#94a3b8",
            cursor: "pointer",
          }}
        >
          {Icon.x(16)}
        </button>
      </div>
      <div
        style={{
          padding: 22,
          display: "flex",
          flexDirection: "column",
          gap: 14,
          maxHeight: "65vh",
          overflow: "auto",
        }}
      >
        <Input
          label="이벤트명"
          placeholder="예: wishlist_added"
          value={categoryName}
          onChange={(e: { target: { value: string } }) =>
            setCategoryName(e.target.value)
          }
          help={
            categoryNameError ??
            "영소문자·숫자·언더스코어 3~40자. SDK 트래킹 키"
          }
          error={!!categoryNameError}
          style={{ fontFamily: "var(--font-mono)" }}
        />
        <Input
          label="표시명"
          placeholder="예: 위시리스트 추가"
          value={displayName}
          onChange={(e: { target: { value: string } }) =>
            setDisplayName(e.target.value)
          }
          help={displayNameError ?? "콘솔 / 분석에서 표시될 한글 라벨"}
          error={!!displayNameError}
        />
        <Input
          label="설명 (선택)"
          placeholder="이 이벤트가 언제 발생하는지"
          value={description}
          onChange={(e: { target: { value: string } }) =>
            setDescription(e.target.value)
          }
        />
        <div>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              marginBottom: 6,
            }}
          >
            <div style={{ fontSize: 12, fontWeight: 500, color: "#334155" }}>
              속성 스키마
            </div>
            <button
              type="button"
              onClick={addRow}
              style={{
                fontSize: 11,
                color: "#3b5bdb",
                cursor: "pointer",
                fontWeight: 600,
                background: "transparent",
                border: 0,
              }}
            >
              + 속성 추가
            </button>
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {props.map((p, i) => (
              <div
                key={i}
                style={{
                  display: "grid",
                  gridTemplateColumns: "1.4fr 0.8fr 0.7fr 0.5fr 30px",
                  gap: 6,
                }}
              >
                <input
                  value={p.key}
                  onChange={(e) => updateRow(i, { key: e.target.value })}
                  placeholder="속성 이름"
                  style={{
                    height: 34,
                    padding: "0 12px",
                    fontFamily: "var(--font-mono)",
                    fontSize: 12,
                    border: "1px solid #e2e8f0",
                    borderRadius: 8,
                    outline: 0,
                  }}
                />
                <select
                  value={p.type}
                  onChange={(e) =>
                    updateRow(i, { type: e.target.value as PropertyType })
                  }
                  style={{
                    height: 34,
                    padding: "0 10px",
                    fontSize: 12,
                    border: "1px solid #e2e8f0",
                    borderRadius: 8,
                    background: "#fff",
                    fontFamily: "inherit",
                  }}
                >
                  {TYPE_OPTIONS.map((t) => (
                    <option key={t}>{t}</option>
                  ))}
                </select>
                <input
                  value={p.example}
                  onChange={(e) => updateRow(i, { example: e.target.value })}
                  placeholder="예시값 (선택)"
                  style={{
                    height: 34,
                    padding: "0 10px",
                    fontFamily: "var(--font-mono)",
                    fontSize: 12,
                    border: "1px solid #e2e8f0",
                    borderRadius: 8,
                    outline: 0,
                  }}
                />
                <label
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Checkbox
                    checked={p.required}
                    onChange={(v: boolean) => updateRow(i, { required: v })}
                    label="필수"
                  />
                </label>
                <button
                  type="button"
                  aria-label="속성 삭제"
                  onClick={() => rmRow(i)}
                  style={{
                    border: 0,
                    background: "transparent",
                    color: "#cbd5e1",
                    cursor: "pointer",
                  }}
                >
                  {Icon.trash(13)}
                </button>
              </div>
            ))}
          </div>
        </div>
      </div>
      <div
        style={{
          padding: "12px 22px",
          borderTop: "1px solid #f1f5f9",
          display: "flex",
          justifyContent: "flex-end",
          gap: 8,
        }}
      >
        <Button variant="ghost" size="md" onClick={close} style={{}}>
          취소
        </Button>
        <Button
          variant="primaryStrong"
          size="md"
          icon={Icon.check(12)}
          onClick={submit}
          disabled={submitting}
          style={{}}
        >
          {submitting ? "생성 중..." : "이벤트 생성"}
        </Button>
      </div>
    </div>
  );
}
