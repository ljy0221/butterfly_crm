import { Tag } from "@/components/common";
import type { Operator, UserNode } from "@/schemas/segment";
import OperatorSelect from "./OperatorSelect";

/**
 * UserNode 편집기 — 사용자 속성 필터.
 *
 * 백엔드 record:
 *   UserNode { key, op: Operator(8종), value: JsonNode }
 *
 * key 선택지(예시):
 *  - birthDate (date)
 *  - joinedAt (date)
 *  - role (string)
 *  - tier (string|number)
 *
 * key 의 타입에 따라 input 분기. 여기서는 birthDate/joinedAt 은 date input,
 * 그 외는 일반 text/number 로 처리한다.
 */

export interface UserNodeEditorProps {
  value: UserNode;
  onChange: (next: UserNode) => void;
  disabled?: boolean;
}

const USER_KEYS = [
  { value: "birthDate", label: "생년월일" },
  { value: "joinedAt", label: "가입일" },
  { value: "role", label: "역할" },
  { value: "tier", label: "등급" },
];

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

function stringifyValue(v: UserNode["value"]): string {
  if (Array.isArray(v)) return v.join(", ");
  if (typeof v === "boolean") return v ? "true" : "false";
  return String(v);
}

function parseValue(
  raw: string,
  op: Operator,
  isDate: boolean,
): string | number | (string | number)[] {
  if (op === "in") {
    return raw
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }
  if (isDate) return raw;
  // numeric coercion
  if (raw !== "" && /^-?\d+(\.\d+)?$/.test(raw)) {
    const n = Number(raw);
    if (Number.isFinite(n)) return n;
  }
  return raw;
}

export default function UserNodeEditor({
  value,
  onChange,
  disabled,
}: UserNodeEditorProps) {
  const isDate = value.key === "birthDate" || value.key === "joinedAt";
  const inputType = isDate && value.op !== "in" ? "date" : "text";

  return (
    <div
      style={{
        display: "flex",
        gap: 6,
        alignItems: "center",
        flexWrap: "wrap",
      }}
    >
      <Tag tone="info" style={{}}>사용자</Tag>
      <input
        list="user-keys"
        value={value.key}
        disabled={disabled}
        onChange={(e) => onChange({ ...value, key: e.target.value })}
        placeholder="key"
        style={{ ...inputStyle, width: 140, fontFamily: "var(--font-mono)" }}
      />
      <datalist id="user-keys">
        {USER_KEYS.map((k) => (
          <option key={k.value} value={k.value}>
            {k.label}
          </option>
        ))}
      </datalist>
      <OperatorSelect
        value={value.op}
        disabled={disabled}
        onChange={(op) =>
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
          })
        }
      />
      <input
        type={inputType}
        value={stringifyValue(value.value)}
        disabled={disabled}
        onChange={(e) =>
          onChange({
            ...value,
            value: parseValue(e.target.value, value.op, isDate),
          })
        }
        placeholder={value.op === "in" ? "쉼표로 구분" : "값"}
        style={{ ...inputStyle, flex: 1, minWidth: 140 }}
      />
    </div>
  );
}
