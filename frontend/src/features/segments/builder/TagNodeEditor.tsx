import { Tag } from "@/components/common";
import type { TagNode } from "@/schemas/segment";
import OperatorSelect from "./OperatorSelect";

/**
 * TagNode 편집기.
 *
 * 백엔드 record:
 *   TagNode { key, op: Operator, value: JsonNode }
 *
 * 백엔드 OperatorCompatibilityValidator: TagNode 는 eq/neq/in/contains 만 허용.
 *
 * op="in" 인 경우 value 를 콤마 split.
 */

export interface TagNodeEditorProps {
  value: TagNode;
  onChange: (next: TagNode) => void;
  disabled?: boolean;
}

const TAG_OP_ALLOWED = ["eq", "neq", "in", "contains"] as const;

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

function stringifyValue(v: TagNode["value"]): string {
  if (Array.isArray(v)) return v.join(", ");
  if (typeof v === "boolean") return v ? "true" : "false";
  return String(v);
}

export default function TagNodeEditor({
  value,
  onChange,
  disabled,
}: TagNodeEditorProps) {
  return (
    <div
      style={{
        display: "flex",
        gap: 6,
        alignItems: "center",
        flexWrap: "wrap",
      }}
    >
      <Tag tone="warning" style={{}}>태그</Tag>
      <input
        value={value.key}
        disabled={disabled}
        onChange={(e) => onChange({ ...value, key: e.target.value })}
        placeholder="태그명"
        style={{ ...inputStyle, width: 160 }}
      />
      <OperatorSelect
        value={value.op}
        allowed={[...TAG_OP_ALLOWED]}
        disabled={disabled}
        onChange={(op) => {
          if (
            op !== "eq" &&
            op !== "neq" &&
            op !== "in" &&
            op !== "contains"
          ) {
            return;
          }
          onChange({
            ...value,
            op,
            value:
              op === "in"
                ? Array.isArray(value.value)
                  ? value.value
                  : [stringifyValue(value.value)]
                : Array.isArray(value.value)
                  ? value.value.join(",")
                  : value.value,
          });
        }}
      />
      <input
        value={stringifyValue(value.value)}
        disabled={disabled}
        onChange={(e) => {
          if (value.op === "in") {
            const parts = e.target.value
              .split(",")
              .map((s) => s.trim())
              .filter((s) => s.length > 0);
            onChange({ ...value, value: parts });
          } else {
            onChange({ ...value, value: e.target.value });
          }
        }}
        placeholder={value.op === "in" ? "쉼표로 구분" : "값"}
        style={{ ...inputStyle, flex: 1, minWidth: 140 }}
      />
    </div>
  );
}
