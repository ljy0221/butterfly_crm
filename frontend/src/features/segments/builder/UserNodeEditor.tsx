import { Tag } from "@/components/common";
import type {
  UserConditionAttribute,
  UserConditionValueType,
} from "@/api/segment";
import type { Operator, UserNode } from "@/schemas/segment";
import OperatorSelect from "./OperatorSelect";

/**
 * UserNode 편집기 — 사용자 속성 필터.
 *
 * 백엔드 record:
 *   UserNode { key, op: Operator(8종), value: JsonNode }
 *
 * 속성 목록과 허용 연산자는 백엔드 condition-metadata API 응답을 사용한다.
 */

export interface UserNodeEditorProps {
  value: UserNode;
  userAttributes: UserConditionAttribute[];
  onChange: (next: UserNode) => void;
  disabled?: boolean;
}

function attributeOf(
  key: string,
  userAttributes: UserConditionAttribute[],
): UserConditionAttribute | undefined {
  return userAttributes.find((item) => item.key === key);
}

function defaultValueFor(type: UserConditionValueType): string | boolean {
  return type === "boolean" ? true : "";
}

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
  type: UserConditionValueType,
): string | number | boolean | (string | number)[] {
  if (op === "in") {
    return raw
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }
  if (type === "date") return raw;
  if (type === "boolean") return raw === "true";
  // numeric coercion
  if (raw !== "" && /^-?\d+(\.\d+)?$/.test(raw)) {
    const n = Number(raw);
    if (Number.isFinite(n)) return n;
  }
  return raw;
}

export default function UserNodeEditor({
  value,
  userAttributes,
  onChange,
  disabled,
}: UserNodeEditorProps) {
  const currentAttribute = attributeOf(value.key, userAttributes);
  const keyType = currentAttribute?.valueType ?? "string";
  const allowedOperators = currentAttribute?.operators ?? [];
  const isDate = keyType === "date";
  const isBoolean = keyType === "boolean";
  const inputType = isDate && value.op !== "in" ? "date" : "text";
  const isKeySelectDisabled = disabled || userAttributes.length === 0;
  const isValueDisabled = disabled || !currentAttribute;

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
      <select
        value={value.key}
        disabled={isKeySelectDisabled}
        onChange={(e) => {
          const nextKey = e.target.value;
          const nextAttribute = attributeOf(nextKey, userAttributes);
          if (!nextAttribute) return;
          const nextType = nextAttribute.valueType;
          const nextAllowedOperators = nextAttribute.operators;
          const nextOp = nextAllowedOperators.includes(value.op)
            ? value.op
            : nextAllowedOperators[0];
          onChange({
            ...value,
            key: nextKey,
            op: nextOp,
            value: defaultValueFor(nextType),
          });
        }}
        style={{ ...inputStyle, width: 150 }}
      >
        {userAttributes.map((k) => (
          <option key={k.key} value={k.key}>
            {k.label}
          </option>
        ))}
      </select>
      <OperatorSelect
        value={value.op}
        disabled={isValueDisabled}
        allowed={allowedOperators}
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
      {isBoolean ? (
        <select
          value={stringifyValue(value.value)}
          disabled={isValueDisabled}
          onChange={(e) =>
            onChange({
              ...value,
              value: parseValue(e.target.value, value.op, keyType),
            })
          }
          style={{ ...inputStyle, flex: 1, minWidth: 140 }}
        >
          <option value="true">동의</option>
          <option value="false">미동의</option>
        </select>
      ) : (
        <input
          type={inputType}
          value={stringifyValue(value.value)}
          disabled={isValueDisabled}
          onChange={(e) =>
            onChange({
              ...value,
              value: parseValue(e.target.value, value.op, keyType),
            })
          }
          placeholder={value.op === "in" ? "쉼표로 구분" : "값"}
          style={{ ...inputStyle, flex: 1, minWidth: 140 }}
        />
      )}
    </div>
  );
}
