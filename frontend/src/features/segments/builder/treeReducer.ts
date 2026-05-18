import {
  emptyGroup,
  type ConditionLogic,
  type ConditionNode,
  type DeviceNode,
  type EventNode,
  type GroupNode,
  type Period,
  type TagNode,
  type UserNode,
} from "@/schemas/segment";

/**
 * Sprint 5 — 조건 트리 reducer.
 *
 * 트리는 useReducer + path(number[]) 기반 mutation 으로 관리.
 * path 는 root group 부터 자식으로 내려가며 conditions[i].conditions[j] 인덱스 시퀀스.
 *
 * 예: path [] = root, [0] = root.conditions[0], [1, 2] = root.conditions[1].conditions[2].
 *
 * 모든 액션은 root GroupNode 를 return 하고, 변경된 부분만 새 객체로 갈아끼운다(structural sharing).
 *
 * P1: immer 도입 검토.
 */

export type LeafKind = "event" | "user" | "tag" | "device";

export type TreeAction =
  | { type: "SET_TREE"; tree: GroupNode }
  | { type: "SET_LOGIC"; path: number[]; logic: ConditionLogic }
  | { type: "ADD_LEAF"; path: number[]; kind: LeafKind; node?: ConditionNode }
  | { type: "ADD_GROUP"; path: number[] }
  | { type: "WRAP_IN_GROUP"; path: number[] }
  | { type: "DUPLICATE"; path: number[] }
  | { type: "DELETE"; path: number[] }
  | { type: "UPDATE_NODE"; path: number[]; node: ConditionNode };

// ---------- Default leaf factories ----------

export function defaultPeriod(): Period {
  return { type: "relative", days: 30 };
}

export function defaultEventNode(): EventNode {
  return {
    type: "event",
    categoryId: 0,
    filters: [],
    period: defaultPeriod(),
    minCount: 1,
  };
}

export function defaultUserNode(): UserNode {
  return {
    type: "user",
    key: "",
    op: "eq",
    value: "",
  };
}

export function defaultTagNode(): TagNode {
  return {
    type: "tag",
    key: "",
    op: "eq",
    value: "",
  };
}

export function defaultDeviceNode(): DeviceNode {
  return {
    type: "device",
    key: "os",
    op: "eq",
    value: "",
  };
}

export function defaultLeaf(kind: LeafKind): ConditionNode {
  switch (kind) {
    case "event":
      return defaultEventNode();
    case "user":
      return defaultUserNode();
    case "tag":
      return defaultTagNode();
    case "device":
      return defaultDeviceNode();
  }
}

// ---------- Initial state ----------

export function initialTree(): GroupNode {
  // 빈 root — 사용자가 [+ 조건] 으로 채워야 함
  return emptyGroup("and");
}

// ---------- Path navigation ----------

/**
 * path 를 따라 노드를 가져온다.
 * path=[] → root, path=[0] → root.conditions[0], ...
 */
export function nodeAt(root: GroupNode, path: number[]): ConditionNode | null {
  let cur: ConditionNode = root;
  for (const i of path) {
    if (cur.type !== "group") return null;
    if (i < 0 || i >= cur.conditions.length) return null;
    cur = cur.conditions[i];
  }
  return cur;
}

/**
 * path 위치에 새 노드를 substitute. 부모 group 들을 모두 새 객체로 복사.
 */
function setAt(
  root: GroupNode,
  path: number[],
  newNode: ConditionNode | null,
): GroupNode {
  if (path.length === 0) {
    if (newNode == null) {
      // root 삭제 — 빈 그룹으로 초기화
      return emptyGroup(root.logic);
    }
    if (newNode.type !== "group") {
      // root 는 반드시 group 이어야 함 — 들어온 값이 leaf 면 wrapping
      return { type: "group", logic: "and", conditions: [newNode] };
    }
    return newNode;
  }
  const [head, ...rest] = path;
  if (root.type !== "group") return root;
  const oldChild = root.conditions[head];
  if (oldChild == null) return root;
  let newChildren: ConditionNode[];
  if (rest.length === 0) {
    if (newNode == null) {
      newChildren = [
        ...root.conditions.slice(0, head),
        ...root.conditions.slice(head + 1),
      ];
    } else {
      newChildren = [
        ...root.conditions.slice(0, head),
        newNode,
        ...root.conditions.slice(head + 1),
      ];
    }
  } else {
    if (oldChild.type !== "group") return root;
    const updatedChild = setAt(oldChild, rest, newNode);
    newChildren = [
      ...root.conditions.slice(0, head),
      updatedChild,
      ...root.conditions.slice(head + 1),
    ];
  }
  return { ...root, conditions: newChildren };
}

/**
 * path 위치에 새 자식 노드를 push (해당 path 가 group 일 것을 가정).
 */
function pushChild(
  root: GroupNode,
  path: number[],
  child: ConditionNode,
): GroupNode {
  if (path.length === 0) {
    return { ...root, conditions: [...root.conditions, child] };
  }
  const target = nodeAt(root, path);
  if (!target || target.type !== "group") return root;
  const updated: GroupNode = {
    ...target,
    conditions: [...target.conditions, child],
  };
  return setAt(root, path, updated);
}

/**
 * path 위치의 노드를 그룹으로 감싼다.
 * leaf X → group { logic:and, conditions:[X] } 로 교체.
 * group X → group { logic:and, conditions:[X] } 로 한 단계 더 감싼다.
 */
function wrapAt(root: GroupNode, path: number[]): GroupNode {
  if (path.length === 0) {
    // root 를 감쌈 — 새 root 는 [기존 root] 를 자식으로 하는 group
    return { type: "group", logic: "and", conditions: [root] };
  }
  const target = nodeAt(root, path);
  if (!target) return root;
  const wrapper: GroupNode = {
    type: "group",
    logic: "and",
    conditions: [target],
  };
  return setAt(root, path, wrapper);
}

/**
 * path 위치 노드를 복제하여 같은 부모의 바로 다음 자리에 삽입.
 */
function duplicateAt(root: GroupNode, path: number[]): GroupNode {
  if (path.length === 0) return root; // root 복제는 의미 X
  const target = nodeAt(root, path);
  if (!target) return root;
  const parentPath = path.slice(0, -1);
  const idx = path[path.length - 1];
  const parent = nodeAt(root, parentPath);
  if (!parent || parent.type !== "group") return root;
  const cloned = structuredClone(target);
  const newChildren = [
    ...parent.conditions.slice(0, idx + 1),
    cloned,
    ...parent.conditions.slice(idx + 1),
  ];
  return setAt(root, parentPath, { ...parent, conditions: newChildren });
}

// ---------- Reducer ----------

export function treeReducer(state: GroupNode, action: TreeAction): GroupNode {
  switch (action.type) {
    case "SET_TREE":
      return action.tree;

    case "SET_LOGIC": {
      const target = nodeAt(state, action.path);
      if (!target || target.type !== "group") return state;
      const updated: GroupNode = { ...target, logic: action.logic };
      return setAt(state, action.path, updated);
    }

    case "ADD_LEAF":
      return pushChild(state, action.path, action.node ?? defaultLeaf(action.kind));

    case "ADD_GROUP":
      return pushChild(state, action.path, emptyGroup("and"));

    case "WRAP_IN_GROUP":
      return wrapAt(state, action.path);

    case "DUPLICATE":
      return duplicateAt(state, action.path);

    case "DELETE":
      return setAt(state, action.path, null);

    case "UPDATE_NODE":
      return setAt(state, action.path, action.node);
  }
}
