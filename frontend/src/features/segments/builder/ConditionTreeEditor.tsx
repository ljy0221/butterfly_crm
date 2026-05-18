import { Icon, Menu } from "@/components/common";
import type { UserConditionAttribute } from "@/api/segment";
import type { ConditionNode } from "@/schemas/segment";
import type { TreeAction } from "./treeReducer";
import EventNodeEditor from "./EventNodeEditor";
import UserNodeEditor from "./UserNodeEditor";
import TagNodeEditor from "./TagNodeEditor";
import DeviceNodeEditor from "./DeviceNodeEditor";
import GroupNodeEditor from "./GroupNodeEditor";

/**
 * 재귀 트리 렌더러.
 *
 * 노드 type 에 따라 leaf editor 또는 GroupNodeEditor 로 분기.
 * leaf 는 카드(흰 배경) + ⋯ 메뉴(그룹화/복제/삭제) 로 감싼다.
 */

export interface ConditionTreeEditorProps {
  node: ConditionNode;
  path: number[];
  dispatch: (action: TreeAction) => void;
  userAttributes: UserConditionAttribute[];
  disabled?: boolean;
  rootDepth: number;
}

export default function ConditionTreeEditor({
  node,
  path,
  dispatch,
  userAttributes,
  disabled,
  rootDepth,
}: ConditionTreeEditorProps) {
  if (node.type === "group") {
    return (
      <GroupNodeEditor
        node={node}
        path={path}
        isRoot={path.length === 0}
        dispatch={dispatch}
        userAttributes={userAttributes}
        disabled={disabled}
        rootDepth={rootDepth}
      />
    );
  }

  // Leaf 카드 wrapper
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 8,
        padding: 12,
        display: "flex",
        gap: 8,
        alignItems: "flex-start",
      }}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        {node.type === "event" && (
          <EventNodeEditor
            value={node}
            disabled={disabled}
            onChange={(next) =>
              dispatch({ type: "UPDATE_NODE", path, node: next })
            }
          />
        )}
        {node.type === "user" && (
          <UserNodeEditor
            value={node}
            userAttributes={userAttributes}
            disabled={disabled}
            onChange={(next) =>
              dispatch({ type: "UPDATE_NODE", path, node: next })
            }
          />
        )}
        {node.type === "tag" && (
          <TagNodeEditor
            value={node}
            disabled={disabled}
            onChange={(next) =>
              dispatch({ type: "UPDATE_NODE", path, node: next })
            }
          />
        )}
        {node.type === "device" && (
          <DeviceNodeEditor
            value={node}
            disabled={disabled}
            onChange={(next) =>
              dispatch({ type: "UPDATE_NODE", path, node: next })
            }
          />
        )}
      </div>
      {!disabled && (
        <Menu
          align="right"
          trigger={
            <button
              type="button"
              aria-label="조건 메뉴"
              style={{
                width: 28,
                height: 28,
                border: 0,
                background: "transparent",
                color: "#64748b",
                cursor: "pointer",
                borderRadius: 6,
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
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
              label: "삭제",
              icon: Icon.trash(12),
              danger: true,
              onClick: () => dispatch({ type: "DELETE", path }),
            },
          ]}
          style={{}}
        />
      )}
    </div>
  );
}
