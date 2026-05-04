import { z } from "zod";

/**
 * Sprint 5 — Segment 도메인 Zod 스키마.
 *
 * 백엔드 sealed interface 5종(EventNode/UserNode/TagNode/DeviceNode/GroupNode) +
 * Period 3종(Relative/Absolute/Cumulative) + Operator 8종을 z.lazy + discriminated union 으로 매핑.
 *
 * 참고:
 *  - backend/butterfly-segment/.../domain/condition/{ConditionNode,EventNode,...}.java
 *  - backend/butterfly-segment/.../domain/condition/{Operator,LogicOp,Period}.java
 *  - JsonNode value 는 string | number | boolean | (string|number)[]; op="in" 인 경우 배열로 직렬화한다.
 */

// ---------- Operator / Logic enum ----------

/**
 * 백엔드 com.b101.butterfly.segment.domain.condition.Operator 와 동일 (8종 소문자 직렬화).
 */
export const OperatorSchema = z.enum([
  "eq",
  "neq",
  "gt",
  "lt",
  "gte",
  "lte",
  "in",
  "contains",
]);
export type Operator = z.infer<typeof OperatorSchema>;

/**
 * 백엔드 com.b101.butterfly.segment.domain.condition.LogicOp.
 */
export const ConditionLogicSchema = z.enum(["and", "or"]);
export type ConditionLogic = z.infer<typeof ConditionLogicSchema>;

/**
 * EventFilter / UserNode / TagNode / DeviceNode 의 value 필드.
 *
 * 백엔드는 JsonNode 로 받지만 UI 단에서는 op 에 따라 정규화한다:
 *  - op="in"     : 배열 (string|number)[]
 *  - op="contains" : string
 *  - 기타        : string | number | boolean
 */
export const ConditionValueSchema: z.ZodType<
  string | number | boolean | (string | number)[]
> = z.union([
  z.string(),
  z.number(),
  z.boolean(),
  z.array(z.union([z.string(), z.number()])),
]);
export type ConditionValue = z.infer<typeof ConditionValueSchema>;

// ---------- Period (discriminated union 3종) ----------

export const RelativePeriodSchema = z.object({
  type: z.literal("relative"),
  /** 최근 N일. 1~3650 (10년). */
  days: z.number().int().min(1, "1일 이상이어야 합니다").max(3650, "3650일 이하여야 합니다"),
});

export const AbsolutePeriodSchema = z.object({
  type: z.literal("absolute"),
  /** yyyy-MM-dd */
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "yyyy-MM-dd 형식"),
  /** yyyy-MM-dd */
  endDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "yyyy-MM-dd 형식"),
});

export const CumulativePeriodSchema = z.object({
  type: z.literal("cumulative"),
});

/**
 * Period (Relative/Absolute/Cumulative) discriminated union.
 *
 * 주의: discriminatedUnion 은 ZodObject 만 받으므로 refine 은 외부 superRefine 으로 적용한다.
 * UI 단에서는 PeriodEditor 가 startDate <= endDate 자체적으로 강제하므로 zod 단위 보조 검증.
 */
export const PeriodSchema = z
  .discriminatedUnion("type", [
    RelativePeriodSchema,
    AbsolutePeriodSchema,
    CumulativePeriodSchema,
  ])
  .superRefine((p, ctx) => {
    if (p.type === "absolute" && p.startDate > p.endDate) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["endDate"],
        message: "시작일은 종료일보다 이전이어야 합니다",
      });
    }
  });
export type Period = z.infer<typeof PeriodSchema>;
export type RelativePeriod = z.infer<typeof RelativePeriodSchema>;
export type AbsolutePeriod = z.infer<typeof AbsolutePeriodSchema>;
export type CumulativePeriod = z.infer<typeof CumulativePeriodSchema>;

// ---------- Leaf nodes (Event/User/Tag/Device) ----------

export const EventFilterSchema = z.object({
  /** 백엔드: productId | price | etc. */
  key: z.string().min(1),
  op: OperatorSchema,
  value: ConditionValueSchema,
});
export type EventFilter = z.infer<typeof EventFilterSchema>;

export const EventNodeSchema = z.object({
  type: z.literal("event"),
  /** 백엔드 EventNode.category — purchase | click | view (확장 가능). */
  category: z.string().min(1),
  filters: z.array(EventFilterSchema),
  period: PeriodSchema,
  /** 최소 발생 횟수 (백엔드 기본값 1). */
  minCount: z.number().int().min(1),
});
export type EventNode = z.infer<typeof EventNodeSchema>;

export const UserNodeSchema = z.object({
  type: z.literal("user"),
  /** 백엔드 UserNode.key — birthDate | joinedAt | etc. */
  key: z.string().min(1),
  op: OperatorSchema,
  value: ConditionValueSchema,
});
export type UserNode = z.infer<typeof UserNodeSchema>;

export const TagNodeSchema = z.object({
  type: z.literal("tag"),
  /** TagNode.key — 태그 이름. */
  key: z.string().min(1),
  /** TagNode 는 eq/neq/in/contains 만 허용 (백엔드 OperatorCompatibilityValidator). */
  op: z.enum(["eq", "neq", "in", "contains"]),
  value: ConditionValueSchema,
});
export type TagNode = z.infer<typeof TagNodeSchema>;

export const DeviceNodeSchema = z.object({
  type: z.literal("device"),
  /** DeviceNode.key — os | deviceType. */
  key: z.enum(["os", "deviceType"]),
  /** DeviceNode 는 eq/neq/in 만 허용. */
  op: z.enum(["eq", "neq", "in"]),
  value: ConditionValueSchema,
});
export type DeviceNode = z.infer<typeof DeviceNodeSchema>;

// ---------- GroupNode (recursive) ----------

/**
 * sealed interface 매핑을 위해 ConditionNode 는 5종 union (4 leaf + group).
 * group 이 자기 자신을 conditions 배열로 가질 수 있으므로 z.lazy + 명시 ZodType 어노테이션.
 */
export interface GroupNode {
  /** 명시적 type 디스크리미네이터. 백엔드는 GroupNode 에 type 필드가 없지만 zod 매칭을 위해 UI 측에서만 부여. */
  type: "group";
  logic: ConditionLogic;
  conditions: ConditionNode[];
}

export type ConditionNode =
  | EventNode
  | UserNode
  | TagNode
  | DeviceNode
  | GroupNode;

/**
 * z.lazy 와 discriminatedUnion 의 조합은 zod 타입 시스템 한계로 ZodType<...> 어노테이션이
 * 정확히 매칭되지 않아 타입 단언으로 우회한다. 런타임 동작은 정상.
 *
 * GroupNodeSchema 는 root 트리이므로 conditions 의 .min(1) 검증이 필요하지만
 * 빈 root 도 UI 진입 시 허용해야 하므로(사용자가 첫 조건을 추가하기 전) 여기서는 검증을 분리한다.
 *  - 빈 root 허용 스키마: groupNodeBaseSchema (UI 트리 직렬화/역직렬화용)
 *  - 저장 직전 검증: createSegmentSchema 가 conditions.min(1) 강제
 */
const groupNodeRecursive: z.ZodType<GroupNode> = z.lazy(() =>
  z.object({
    type: z.literal("group"),
    logic: ConditionLogicSchema,
    conditions: z.array(conditionNodeRecursive),
  }),
) as z.ZodType<GroupNode>;

const conditionNodeRecursive: z.ZodType<ConditionNode> = z.lazy(() =>
  z.union([
    EventNodeSchema,
    UserNodeSchema,
    TagNodeSchema,
    DeviceNodeSchema,
    groupNodeRecursive as unknown as z.ZodType<ConditionNode>,
  ]),
) as z.ZodType<ConditionNode>;

export const GroupNodeSchema = groupNodeRecursive;
export const ConditionNodeSchema = conditionNodeRecursive;

// ---------- 필드 단위 스키마 ----------

export const nameSchema = z
  .string()
  .min(1, "세그먼트 이름은 1자 이상이어야 합니다")
  .max(30, "세그먼트 이름은 30자 이하여야 합니다");

export const descriptionSchema = z
  .string()
  .max(100, "설명은 100자 이하여야 합니다")
  .default("");

// ---------- 폼 스키마 ----------

/**
 * 저장용 root group: conditions 가 1개 이상이어야 함.
 *
 * GroupNodeSchema 는 빈 root 도 허용하지만(UI 초기 상태), 저장 시점에는 1개 이상 강제.
 */
const filledRootGroupSchema = z
  .object({
    type: z.literal("group"),
    logic: ConditionLogicSchema,
    conditions: z.array(ConditionNodeSchema).min(1, "조건은 1개 이상이어야 합니다"),
  })
  .passthrough() as unknown as z.ZodType<GroupNode>;

export const createSegmentSchema = z.object({
  name: nameSchema,
  description: descriptionSchema,
  conditionTree: filledRootGroupSchema,
});
export type CreateSegmentInput = z.infer<typeof createSegmentSchema>;

export const updateSegmentSchema = createSegmentSchema;
export type UpdateSegmentInput = z.infer<typeof updateSegmentSchema>;

export const previewRequestSchema = z.object({
  conditionTree: filledRootGroupSchema,
});
export type PreviewRequestInput = z.infer<typeof previewRequestSchema>;

// ---------- 유틸리티 ----------

/**
 * 비어 있는 GroupNode (UI 초기 상태용).
 * 빈 conditions 로 시작하고 사용자가 [+ 조건] 으로 채운다.
 */
export function emptyGroup(logic: ConditionLogic = "and"): GroupNode {
  return { type: "group", logic, conditions: [] };
}

/** UI 표기용 — type=group 을 백엔드 직렬화 시 제거. */
export interface BackendGroupNode {
  logic: ConditionLogic;
  conditions: BackendConditionNode[];
}
export type BackendConditionNode =
  | (Omit<EventNode, "type"> & { type: "event" })
  | (Omit<UserNode, "type"> & { type: "user" })
  | (Omit<TagNode, "type"> & { type: "tag" })
  | (Omit<DeviceNode, "type"> & { type: "device" })
  | BackendGroupNode;

/**
 * UI GroupNode → 백엔드 GroupNode (type 필드 제거).
 *
 * 백엔드 GroupNode 는 type 필드가 없으므로 UI 측에서만 추가한 디스크리미네이터를
 * 직렬화 직전에 떼어낸다. ConditionNode 는 type 을 유지한 채 전송 (Jackson polymorphic).
 */
export function toBackendTree(group: GroupNode): BackendGroupNode {
  return {
    logic: group.logic,
    conditions: group.conditions.map(toBackendNode),
  };
}

function toBackendNode(node: ConditionNode): BackendConditionNode {
  if (node.type === "group") {
    return toBackendTree(node);
  }
  return node;
}

/**
 * 백엔드 응답 GroupNode → UI GroupNode (type=group 부여).
 *
 * 응답은 GroupNode 에 type 이 없으므로 UI 측에서 보강. 자식 노드 중 logic 키가 있으면 group 으로 판정.
 */
export function fromBackendTree(group: BackendGroupNode): GroupNode {
  return {
    type: "group",
    logic: group.logic,
    conditions: group.conditions.map(fromBackendNode),
  };
}

function fromBackendNode(node: BackendConditionNode): ConditionNode {
  if ("logic" in node) {
    return fromBackendTree(node);
  }
  return node;
}

/**
 * 트리 깊이 측정 (root group=깊이 0, 자식 group=1, ...).
 * UI 가드: 깊이 3 이상의 그룹 안에서는 [+ 그룹] 비활성.
 */
export function treeDepth(node: ConditionNode): number {
  if (node.type !== "group") return 0;
  if (node.conditions.length === 0) return 0;
  return 1 + Math.max(...node.conditions.map(treeDepth));
}
