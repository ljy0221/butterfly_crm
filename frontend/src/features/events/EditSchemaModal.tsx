import { useState } from "react";
import { Button, Checkbox, Icon } from "@/components/common";
import { confirmDialog, toast } from "@/lib/ui-bus";
import { mapEventError } from "@/lib/event-errors";
import { useUpdateSchemaMutation } from "@/hooks/queries/event";
import {
  PropertyTypeSchema,
  propertyKeySchema,
  type PropertyType,
} from "@/schemas/event";
import type { PropertyRecord } from "@/api/event";

/**
 * Sprint 7 — 속성 스키마 편집 모달.
 *
 * 카테고리 단위로 PropertyRecord[] 를 한 번에 갱신.
 * 변경을 적용하기 전 confirmDialog 로 사용 세그먼트 모수 재계산 안내.
 */

interface Props {
  projectId: number;
  categoryName: string;
  initial: PropertyRecord[];
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

function toRows(records: PropertyRecord[]): PropRow[] {
  return records.map((r) => ({
    key: r.key,
    type: r.type,
    required: r.required,
    example: r.example ?? "",
  }));
}

export default function EditSchemaModal({
  projectId,
  categoryName,
  initial,
  close,
}: Props) {
  const [rows, setRows] = useState<PropRow[]>(toRows(initial));
  const [submitting, setSubmitting] = useState(false);
  const updateMut = useUpdateSchemaMutation(projectId);

  const addRow = (): void =>
    setRows((p) => [
      ...p,
      { key: "", type: "string", required: false, example: "" },
    ]);
  const rmRow = (i: number): void =>
    setRows((p) => p.filter((_, j) => j !== i));
  const updateRow = (i: number, patch: Partial<PropRow>): void =>
    setRows((p) => p.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  const submit = async (): Promise<void> => {
    const cleaned: PropertyRecord[] = [];
    const seen = new Set<string>();
    for (const p of rows) {
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

    const ok = await confirmDialog({
      title: "스키마를 변경하시겠습니까?",
      message:
        "이 이벤트를 사용하는 세그먼트의 예상 모수가 재계산될 수 있습니다.",
      confirmText: "변경 적용",
      cancelText: "취소",
      tone: "danger",
    });
    if (!ok) return;

    setSubmitting(true);
    try {
      await updateMut.mutateAsync({
        categoryName,
        properties: cleaned,
      });
      toast("스키마를 업데이트했습니다", "success");
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
        width: 640,
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
            속성 스키마 편집
          </div>
          <div
            style={{
              fontSize: 12,
              color: "#64748b",
              marginTop: 2,
              fontFamily: "var(--font-mono)",
            }}
          >
            {categoryName}
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
          gap: 10,
          maxHeight: "65vh",
          overflow: "auto",
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            marginBottom: 6,
          }}
        >
          <div style={{ fontSize: 12, color: "#64748b" }}>
            속성 추가/삭제 후 [변경 적용] 버튼을 누르세요.
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
        {rows.map((p, i) => (
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
          {submitting ? "저장 중..." : "변경 적용"}
        </Button>
      </div>
    </div>
  );
}
