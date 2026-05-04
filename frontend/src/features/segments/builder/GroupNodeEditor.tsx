import { Icon, Menu, Tag } from "@/components/common";
import type { ConditionLogic, GroupNode } from "@/schemas/segment";
import { treeDepth } from "@/schemas/segment";
import type { LeafKind, TreeAction } from "./treeReducer";
import ConditionTreeEditor from "./ConditionTreeEditor";

/**
 * GroupNodeEditor — 재귀 그룹 렌더러.
 *
 * 좌측 logic 배지(AND=info, OR=warning) + AND/OR 토글 + 자식 매핑 +
 * [+ 조건] / [+ 그룹] / ⋯ 메뉴.
 *
 * 깊이 제한: 현재 path 의 깊이가 3 이상이면 [+ 그룹] 비활성.
 */

export interface GroupNodeEditorProps {
  node: GroupNode;
  path: number[];
  /** root 여부. true 이면 ⋯ 삭제 메뉴를 노출하지 않음. */
  isRoot: boolean;
  dispatch: (action: TreeAction) => void;
  disabled?: boolean;
  /** 가장 바깥 root 트리 깊이 — depth check 에 사용. */
  rootDepth: number;
}

const MAX_DEPTH = 3;

const LOGIC_LABEL: Record<ConditionLogic, string> = {
  and: "모두 만족 (AND)",
  or: "하나라도 만족 (OR)",
};

export default function GroupNodeEditor({
  node,
  path,
  isRoot,
  dispatch,
  disabled,
  rootDepth,
}: GroupNodeEditorProps) {
  const currentDepth = path.length;
  const childGroupDepth = treeDepth(node);
  // 이 group 안에 [+ 그룹] 을 더 만들면 child group 의 depth 가 1 됨 — 전체 깊이 = currentDepth + 1
  const canAddGroup = currentDepth + 1 + childGroupDepth <= MAX_DEPTH;

  const setLogic = (logic: ConditionLogic): void => {
    dispatch({ type: "SET_LOGIC", path, logic });
  };

  const addLeaf = (kind: LeafKind): void => {
    dispatch({ type: "ADD_LEAF", path, kind });
  };

  const addGroup = (): void => {
    if (!canAddGroup) return;
    dispatch({ type: "ADD_GROUP", path });
  };

  return (
    <div
      style={{
        position: "relative",
        border: "1px solid #e2e8f0",
        borderRadius: 10,
        background: isRoot ? "#fff" : "#fafbfe",
        padding: 14,
      }}
    >
      {/* 좌측 logic 배지 (시각적 강조) */}
      <div
        aria-hidden
        style={{
          position: "absolute",
          left: -1,
          top: -1,
          bottom: -1,
          width: 4,
          background: node.logic === "and" ? "#3b5bdb" : "#f59e0b",
          borderTopLeftRadius: 10,
          borderBottomLeftRadius: 10,
        }}
      />

      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          marginBottom: node.conditions.length > 0 ? 10 : 0,
          gap: 8,
          flexWrap: "wrap",
        }}
      >
        {/* AND/OR 토글 */}
        <div
          role="radiogroup"
          aria-label="조건 결합 방식"
          style={{
            display: "inline-flex",
            gap: 0,
            padding: 3,
            background: "#f1f5f9",
            borderRadius: 7,
          }}
        >
          {(["and", "or"] as ConditionLogic[]).map((l) => (
            <button
              key={l}
              type="button"
              role="radio"
              aria-checked={node.logic === l}
              disabled={disabled}
              onClick={() => setLogic(l)}
              style={{
                padding: "4px 12px",
                fontSize: 11,
                fontWeight: 700,
                border: 0,
                borderRadius: 5,
                background: node.logic === l ? "#fff" : "transparent",
                color: node.logic === l ? "#1f2937" : "#64748b",
                cursor: disabled ? "not-allowed" : "pointer",
                fontFamily: "inherit",
                boxShadow:
                  node.logic === l ? "0 1px 2px rgba(0,0,0,.06)" : "none",
              }}
            >
              {l.toUpperCase()}
            </button>
          ))}
        </div>
        <span style={{ fontSize: 11, color: "#94a3b8" }}>
          {LOGIC_LABEL[node.logic]}
        </span>

        <div style={{ flex: 1 }} />

        {!isRoot && !disabled && (
          <Menu
            align="right"
            trigger={
              <button
                type="button"
                aria-label="그룹 메뉴"
                style={{
                  width: 28,
                  height: 28,
                  border: "1px solid #e2e8f0",
                  background: "#fff",
                  borderRadius: 6,
                  cursor: "pointer",
                  color: "#64748b",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                {Icon.dots(14)}
              </button>
            }
            items={[
              {
                label: "그룹으로 감싸기",
                icon: Icon.copy(12),
                onClick: () => dispatch({ type: "WRAP_IN_GROUP", path }),
              },
              {
                label: "복제",
                icon: Icon.copy(12),
                onClick: () => dispatch({ type: "DUPLICATE", path }),
              },
              { divider: true },
              {
                label: "그룹 삭제",
                icon: Icon.trash(12),
                danger: true,
                onClick: () => dispatch({ type: "DELETE", path }),
              },
            ]}
            style={{}}
          />
        )}
      </div>

      {/* Children */}
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {node.conditions.map((child, idx) => (
          <div key={idx}>
            {idx > 0 && (
              <div
                aria-hidden
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  margin: "2px 0 6px 8px",
                }}
              >
                <span
                  style={{
                    fontSize: 10,
                    fontWeight: 700,
                    color: node.logic === "and" ? "#3b5bdb" : "#b45309",
                    letterSpacing: 0.5,
                  }}
                >
                  {node.logic.toUpperCase()}
                </span>
                <span style={{ flex: 1, height: 1, background: "#f1f5f9" }} />
              </div>
            )}
            <ConditionTreeEditor
              node={child}
              path={[...path, idx]}
              dispatch={dispatch}
              disabled={disabled}
              rootDepth={rootDepth}
            />
          </div>
        ))}
        {node.conditions.length === 0 && (
          <div
            style={{
              padding: "12px 0",
              fontSize: 12,
              color: "#94a3b8",
              textAlign: "center",
            }}
          >
            아래에서 조건을 추가하세요.
          </div>
        )}
      </div>

      {/* Add row */}
      {!disabled && (
        <div
          style={{
            display: "flex",
            gap: 6,
            marginTop: 12,
            flexWrap: "wrap",
            position: "relative",
          }}
        >
          <Menu
            align="left"
            trigger={
              <button type="button" style={addBtnStyle}>
                {Icon.plus(12)} 조건 추가
              </button>
            }
            items={[
              {
                label: "이벤트 조건",
                icon: <span style={{ color: "#3b5bdb" }}>●</span>,
                onClick: () => addLeaf("event"),
              },
              {
                label: "사용자 속성",
                icon: <span style={{ color: "#3b5bdb" }}>●</span>,
                onClick: () => addLeaf("user"),
              },
              {
                label: "태그",
                icon: <span style={{ color: "#f59e0b" }}>●</span>,
                onClick: () => addLeaf("tag"),
              },
              {
                label: "디바이스",
                icon: <span style={{ color: "#16a34a" }}>●</span>,
                onClick: () => addLeaf("device"),
              },
            ]}
            style={{}}
          />
          <button
            type="button"
            onClick={addGroup}
            disabled={!canAddGroup}
            style={{
              ...addBtnStyle,
              opacity: canAddGroup ? 1 : 0.5,
              cursor: canAddGroup ? "pointer" : "not-allowed",
            }}
            title={
              canAddGroup
                ? "중첩 그룹 추가"
                : `최대 ${MAX_DEPTH}단계까지 중첩 가능합니다`
            }
          >
            {Icon.plus(12)} 그룹
          </button>
          {currentDepth === 0 && (
            <Tag tone="default" style={{ marginLeft: 4 }}>
              깊이 {currentDepth}/{MAX_DEPTH}
            </Tag>
          )}
        </div>
      )}
    </div>
  );
}

const addBtnStyle: React.CSSProperties = {
  height: 32,
  padding: "0 12px",
  fontSize: 12,
  fontWeight: 500,
  border: "1px dashed #cbd5e1",
  borderRadius: 8,
  background: "#fff",
  color: "#64748b",
  cursor: "pointer",
  fontFamily: "inherit",
  display: "inline-flex",
  alignItems: "center",
  gap: 6,
};
