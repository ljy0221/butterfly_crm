import type { Operator } from "@/schemas/segment";

/**
 * 8종 Operator 드롭다운.
 *
 * allowed 로 부분집합 제한 가능 (TagNode/DeviceNode 는 백엔드 호환성 검증으로 일부 op 만 허용).
 *  - TagNode    : eq | neq | in | contains
 *  - DeviceNode : eq | neq | in
 *  - 그 외       : 8종 전부
 */

const ALL_OPERATORS: Operator[] = [
  "eq",
  "neq",
  "gt",
  "lt",
  "gte",
  "lte",
  "in",
  "contains",
];

const OPERATOR_LABEL: Record<Operator, string> = {
  eq: "= 같음",
  neq: "≠ 다름",
  gt: "> 초과",
  lt: "< 미만",
  gte: "≥ 이상",
  lte: "≤ 이하",
  in: "포함됨 (in)",
  contains: "포함 (contains)",
};

export interface OperatorSelectProps {
  value: Operator;
  onChange: (op: Operator) => void;
  allowed?: Operator[];
  disabled?: boolean;
}

export default function OperatorSelect({
  value,
  onChange,
  allowed,
  disabled,
}: OperatorSelectProps) {
  const options = allowed ?? ALL_OPERATORS;
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value as Operator)}
      disabled={disabled}
      style={{
        height: 30,
        padding: "0 28px 0 10px",
        border: "1px solid #e2e8f0",
        borderRadius: 6,
        background: disabled ? "#f8fafc" : "#fff",
        fontFamily: "inherit",
        fontSize: 12,
        color: "#1f2937",
        outline: "none",
        appearance: "none",
        cursor: disabled ? "not-allowed" : "pointer",
        backgroundImage:
          'url("data:image/svg+xml;utf8,<svg xmlns=\'http://www.w3.org/2000/svg\' width=\'10\' height=\'10\' viewBox=\'0 0 24 24\' fill=\'none\' stroke=\'%2394a3b8\' stroke-width=\'2\' stroke-linecap=\'round\' stroke-linejoin=\'round\'><polyline points=\'6 9 12 15 18 9\'></polyline></svg>")',
        backgroundRepeat: "no-repeat",
        backgroundPosition: "right 8px center",
      }}
    >
      {options.map((op) => (
        <option key={op} value={op}>
          {OPERATOR_LABEL[op]}
        </option>
      ))}
    </select>
  );
}
