/**
 * 기존 Sidebar/Topbar/CommandPalette는 "대시보드", "캠페인-신규", "캠페인-상세:cmp_4821"
 * 같은 한글 라벨 기반 라우팅을 사용한다. React Router로 마이그레이션하면서도
 * 그 어휘를 유지하기 위해, label ↔ path 매핑을 한 곳에 둔다.
 */

const PRIMARY_TO_PATH: Record<string, string> = {
  대시보드: "dashboard",
  이벤트: "events",
  고객: "customers",
  세그먼트: "segments",
  캠페인: "campaigns",
  자동화: "journey",
  템플릿: "templates",
  분석: "analytics",
  설정: "settings",
};

const PATH_TO_PRIMARY: Record<string, string> = Object.fromEntries(
  Object.entries(PRIMARY_TO_PATH).map(([k, v]) => [v, k]),
);

const SUB_TO_SEGMENT: Record<string, string> = {
  신규: "new",
  상세: "", // detail은 :id로 들어가므로 별도 segment 없음
};

/**
 * "캠페인", "캠페인-신규", "캠페인-상세:cmp_4821" → "/p/:pid/campaigns/..."
 */
export function labelToPath(label: string, projectId: number | string): string {
  const [head, paramRaw] = label.split(":");
  const [primary, sub] = head.split("-");
  const base = PRIMARY_TO_PATH[primary];
  if (!base) return `/p/${projectId}/dashboard`;

  if (!sub) return `/p/${projectId}/${base}`;
  if (sub === "신규") return `/p/${projectId}/${base}/new`;
  if (sub === "상세" && paramRaw) return `/p/${projectId}/${base}/${paramRaw}`;
  // fallback
  const segment = SUB_TO_SEGMENT[sub] ?? sub;
  return segment ? `/p/${projectId}/${base}/${segment}` : `/p/${projectId}/${base}`;
}

/**
 * pathname에서 활성 primary 라벨(한글) 추출. Sidebar의 active prop에 사용.
 */
export function pathToActiveLabel(pathname: string): string {
  const match = pathname.match(/^\/p\/[^/]+\/([^/]+)/);
  if (!match) return "";
  return PATH_TO_PRIMARY[match[1]] ?? "";
}

export const PRIMARY_LABELS = Object.keys(PRIMARY_TO_PATH);
