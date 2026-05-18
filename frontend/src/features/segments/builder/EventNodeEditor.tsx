import { useEffect, useMemo, useState } from "react";
import { Tag } from "@/components/common";
import type { EventFilter, EventNode, Operator } from "@/schemas/segment";
import { useEventCategoriesQuery, useEventSchemaQuery } from "@/hooks/queries/event";
import { useProjectStore } from "@/stores/project";
import OperatorSelect from "./OperatorSelect";
import PeriodEditor from "./PeriodEditor";

/**
 * EventNode 편집기.
 *
 * 백엔드 record:
 *   EventNode { categoryId, filters: List<EventFilter>, period: Period, minCount: Integer }
 *   EventFilter { key, op: Operator, value: JsonNode }
 *
 * UI 전제:
 *  - categoryId: 이벤트 카테고리 목록에서 선택
 *  - filters[]: 추가/삭제 가능한 행
 *  - period: PeriodEditor
 *  - minCount: 정수 input
 */

export interface EventNodeEditorProps {
  value: EventNode;
  onChange: (next: EventNode) => void;
  disabled?: boolean;
}

const FALLBACK_FILTER_KEYS = ["product_id", "price", "quantity", "category"];

const inputStyle: React.CSSProperties = {
  height: 30,
  padding: "0 10px",
  border: "1px solid #e2e8f0",
  borderRadius: 6,
  background: "#fff",
  fontFamily: "inherit",
  fontSize: 12,
  color: "#1f2937",
  outline: "none",
  boxSizing: "border-box",
};

function parseFilterValue(
  raw: string,
  op: Operator,
): string | number | (string | number)[] {
  if (op === "in") {
    const parts = raw
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
    return parts.map((p) => {
      const n = Number(p);
      return Number.isFinite(n) && p !== "" && /^-?\d+(\.\d+)?$/.test(p) ? n : p;
    });
  }
  // 숫자처럼 보이면 number, 아니면 string
  const n = Number(raw);
  if (raw !== "" && /^-?\d+(\.\d+)?$/.test(raw) && Number.isFinite(n)) {
    return n;
  }
  return raw;
}

function stringifyFilterValue(
  v: EventFilter["value"],
): string {
  if (Array.isArray(v)) return v.join(", ");
  if (typeof v === "boolean") return v ? "true" : "false";
  return String(v);
}

export default function EventNodeEditor({
  value,
  onChange,
  disabled,
}: EventNodeEditorProps) {
  const [minCountText, setMinCountText] = useState(String(value.minCount));
  const projectId = useProjectStore((s) => s.current?.projectId ?? null);
  const categoriesQuery = useEventCategoriesQuery(projectId);
  const categories = useMemo(
    () => categoriesQuery.data ?? [],
    [categoriesQuery.data],
  );
  const selectedCategory = categories.find((c) => c.categoryId === value.categoryId);
  const schemaQuery = useEventSchemaQuery(
    projectId,
    selectedCategory?.categoryName ?? null,
    value.categoryId > 0,
  );
  const filterKeys =
    schemaQuery.data && schemaQuery.data.length > 0
      ? schemaQuery.data.map((p) => p.key)
      : FALLBACK_FILTER_KEYS;

  useEffect(() => {
    if (disabled || value.categoryId > 0 || categories.length === 0) return;
    onChange({ ...value, categoryId: categories[0].categoryId });
  }, [categories, disabled, onChange, value]);

  useEffect(() => {
    setMinCountText(String(value.minCount));
  }, [value.minCount]);

  const updateFilter = (idx: number, next: EventFilter): void => {
    const filters = value.filters.map((f, i) => (i === idx ? next : f));
    onChange({ ...value, filters });
  };

  const addFilter = (): void => {
    onChange({
      ...value,
      filters: [...value.filters, { key: filterKeys[0], op: "eq", value: "" }],
    });
  };

  const removeFilter = (idx: number): void => {
    onChange({
      ...value,
      filters: value.filters.filter((_, i) => i !== idx),
    });
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          flexWrap: "wrap",
        }}
      >
        <Tag tone="info" style={{}}>이벤트</Tag>
        <span style={{ fontSize: 11, color: "#64748b" }}>카테고리</span>
        <select
          value={value.categoryId > 0 ? String(value.categoryId) : ""}
          disabled={disabled || categoriesQuery.isLoading || categories.length === 0}
          onChange={(e) =>
            onChange({ ...value, categoryId: Number(e.target.value) })
          }
          style={{ ...inputStyle, minWidth: 180 }}
        >
          <option value="" disabled>
            {categoriesQuery.isLoading ? "불러오는 중" : "선택"}
          </option>
          {categories.map((c) => (
            <option key={c.categoryId} value={c.categoryId}>
              {c.displayName} ({c.categoryName})
            </option>
          ))}
        </select>
        <span style={{ fontSize: 11, color: "#64748b" }}>최소</span>
        <input
          type="number"
          min={1}
          value={minCountText}
          disabled={disabled}
          onChange={(e) => {
            const raw = e.target.value;
            if (!/^\d*$/.test(raw)) return;
            setMinCountText(raw);
            if (raw === "") return;
            const parsed = Number(raw);
            if (Number.isSafeInteger(parsed) && parsed >= 1) {
              onChange({ ...value, minCount: parsed });
            }
          }}
          onBlur={() => {
            const parsed = Number(minCountText);
            const next =
              Number.isSafeInteger(parsed) && parsed >= 1 ? parsed : 1;
            setMinCountText(String(next));
            if (next !== value.minCount) {
              onChange({ ...value, minCount: next });
            }
          }}
          style={{ ...inputStyle, width: 70 }}
        />
        <span style={{ fontSize: 11, color: "#64748b" }}>회 발생</span>
      </div>

      {/* 기간 */}
      <div
        style={{
          display: "flex",
          gap: 12,
          alignItems: "flex-start",
          flexWrap: "wrap",
        }}
      >
        <span
          style={{
            fontSize: 11,
            color: "#64748b",
            paddingTop: 8,
            minWidth: 36,
          }}
        >
          기간
        </span>
        <div style={{ flex: 1, minWidth: 240 }}>
          <PeriodEditor
            value={value.period}
            disabled={disabled}
            onChange={(period) => onChange({ ...value, period })}
          />
        </div>
      </div>

      {/* 필터 */}
      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
          }}
        >
          <span style={{ fontSize: 11, color: "#64748b" }}>
            추가 필터 {value.filters.length > 0 && `(${value.filters.length})`}
          </span>
          {!disabled && (
            <button
              type="button"
              onClick={addFilter}
              style={{
                fontSize: 11,
                fontWeight: 600,
                color: "#3b5bdb",
                background: "transparent",
                border: 0,
                cursor: "pointer",
                padding: 0,
              }}
            >
              + 필터 추가
            </button>
          )}
        </div>

        {value.filters.map((f, idx) => (
          <div
            key={idx}
            style={{
              display: "flex",
              gap: 6,
              alignItems: "center",
              flexWrap: "wrap",
              padding: 8,
              background: "#f8fafc",
              border: "1px solid #e2e8f0",
              borderRadius: 6,
            }}
          >
            <input
              list={`filter-keys-${idx}`}
              value={f.key}
              disabled={disabled}
              onChange={(e) => updateFilter(idx, { ...f, key: e.target.value })}
              placeholder="key"
              style={{ ...inputStyle, width: 120, fontFamily: "var(--font-mono)" }}
            />
            <datalist id={`filter-keys-${idx}`}>
              {filterKeys.map((k) => (
                <option key={k} value={k} />
              ))}
            </datalist>
            <OperatorSelect
              value={f.op}
              disabled={disabled}
              onChange={(op) =>
                updateFilter(idx, {
                  ...f,
                  op,
                  // op가 in으로 바뀌면 value를 배열로, 아니면 평탄화
                  value:
                    op === "in"
                      ? Array.isArray(f.value)
                        ? f.value
                        : [stringifyFilterValue(f.value)]
                      : Array.isArray(f.value)
                        ? f.value.join(",")
                        : f.value,
                })
              }
            />
            <input
              value={stringifyFilterValue(f.value)}
              disabled={disabled}
              onChange={(e) =>
                updateFilter(idx, {
                  ...f,
                  value: parseFilterValue(e.target.value, f.op),
                })
              }
              placeholder={f.op === "in" ? "쉼표로 구분" : "값"}
              style={{ ...inputStyle, flex: 1, minWidth: 120 }}
            />
            {!disabled && (
              <button
                type="button"
                onClick={() => removeFilter(idx)}
                aria-label="필터 삭제"
                style={{
                  border: 0,
                  background: "transparent",
                  color: "#94a3b8",
                  cursor: "pointer",
                  padding: 4,
                  fontSize: 14,
                  lineHeight: 1,
                }}
              >
                ×
              </button>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
