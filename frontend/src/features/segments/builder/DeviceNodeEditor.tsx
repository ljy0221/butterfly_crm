import { Tag } from "@/components/common";
import type { DeviceNode } from "@/schemas/segment";
import OperatorSelect from "./OperatorSelect";

/**
 * DeviceNode 편집기.
 *
 * 백엔드 record:
 *   DeviceNode { key, op: Operator, value: JsonNode }
 *
 * 백엔드 OperatorCompatibilityValidator: DeviceNode 는 eq/neq/in 만 허용.
 *
 * key:
 *  - os         → iOS / Android / Web
 *  - deviceType → mobile / tablet / desktop
 */

export interface DeviceNodeEditorProps {
  value: DeviceNode;
  onChange: (next: DeviceNode) => void;
  disabled?: boolean;
}

const DEVICE_OP_ALLOWED = ["eq", "neq", "in"] as const;

const KEY_OPTIONS: Record<DeviceNode["key"], string[]> = {
  os: ["iOS", "Android", "Web"],
  deviceType: ["mobile", "tablet", "desktop"],
};

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

const selectStyle: React.CSSProperties = {
  ...inputStyle,
  appearance: "none",
  paddingRight: 28,
  cursor: "pointer",
  backgroundImage:
    'url("data:image/svg+xml;utf8,<svg xmlns=\'http://www.w3.org/2000/svg\' width=\'10\' height=\'10\' viewBox=\'0 0 24 24\' fill=\'none\' stroke=\'%2394a3b8\' stroke-width=\'2\' stroke-linecap=\'round\' stroke-linejoin=\'round\'><polyline points=\'6 9 12 15 18 9\'></polyline></svg>")',
  backgroundRepeat: "no-repeat",
  backgroundPosition: "right 8px center",
};

function stringifyValue(v: DeviceNode["value"]): string {
  if (Array.isArray(v)) return v.join(", ");
  if (typeof v === "boolean") return v ? "true" : "false";
  return String(v);
}

export default function DeviceNodeEditor({
  value,
  onChange,
  disabled,
}: DeviceNodeEditorProps) {
  const valueOptions = KEY_OPTIONS[value.key];
  const isInOp = value.op === "in";

  return (
    <div
      style={{
        display: "flex",
        gap: 6,
        alignItems: "center",
        flexWrap: "wrap",
      }}
    >
      <Tag tone="success" style={{}}>디바이스</Tag>
      <select
        value={value.key}
        disabled={disabled}
        onChange={(e) =>
          onChange({
            ...value,
            key: e.target.value as DeviceNode["key"],
            value: isInOp ? [] : "",
          })
        }
        style={selectStyle}
      >
        <option value="os">OS</option>
        <option value="deviceType">기기 종류</option>
      </select>
      <OperatorSelect
        value={value.op}
        allowed={[...DEVICE_OP_ALLOWED]}
        disabled={disabled}
        onChange={(op) => {
          if (op !== "eq" && op !== "neq" && op !== "in") return;
          onChange({
            ...value,
            op,
            value:
              op === "in"
                ? Array.isArray(value.value)
                  ? value.value
                  : value.value
                    ? [stringifyValue(value.value)]
                    : []
                : Array.isArray(value.value)
                  ? value.value[0] ?? ""
                  : value.value,
          });
        }}
      />
      {isInOp ? (
        <input
          value={stringifyValue(value.value)}
          disabled={disabled}
          onChange={(e) => {
            const parts = e.target.value
              .split(",")
              .map((s) => s.trim())
              .filter((s) => s.length > 0);
            onChange({ ...value, value: parts });
          }}
          placeholder={`예: ${valueOptions.slice(0, 2).join(", ")}`}
          style={{ ...inputStyle, flex: 1, minWidth: 160 }}
        />
      ) : (
        <select
          value={stringifyValue(value.value)}
          disabled={disabled}
          onChange={(e) => onChange({ ...value, value: e.target.value })}
          style={{ ...selectStyle, flex: 1, minWidth: 140 }}
        >
          <option value="">선택…</option>
          {valueOptions.map((v) => (
            <option key={v} value={v}>
              {v}
            </option>
          ))}
        </select>
      )}
    </div>
  );
}
