/**
 * Mock fallback 어댑터.
 *
 * 백엔드가 미구현인 API는 여기에 등록한다.
 * `VITE_USE_MOCK=true` 이거나 도메인별 플래그가 켜진 경우, 실제 axios 호출 대신
 * 이 어댑터의 응답이 반환된다. 호출 측은 `ifMock(domain, fn)` 패턴을 사용한다.
 *
 * 예:
 *   export async function listTemplates(): Promise<Template[]> {
 *     return ifMock("template", () => mockTemplates(), () => api.get("/api/v1/templates").then((r) => r.data));
 *   }
 *
 * ⚠️ 5/15 Code Freeze 점검 체크리스트 (분석/리포트 도메인 영향 위주):
 *   1. VITE_USE_MOCK=true가 .env.production / CI 빌드에 섞이지 않는지 확인
 *   2. campaign 플래그: 캠페인 도메인 구현 시 false 전환. 분석 화면(Dashboard, AnalyticsHome,
 *      CampaignFunnel, SegmentChannelMatrix)은 mock 의존도가 없지만, getCampaignStats만
 *      campaign 플래그를 본다. Sprint 7에 분석 시연이 시작되면 false 권장.
 *   3. mock 응답 record 신규 필드 추가 시 zod 스키마(schemas/analytics.ts, schemas/report.ts,
 *      schemas/funnel.ts) 동기화 필요 — 미동기화 시 zod parse 단계에서 throw.
 *   4. 발표 dry-run(5/22) 직전 dev console에서 "[mock]" prefix 로그가 분석 화면에 출력되지
 *      않는지 확인.
 */

const GLOBAL_FLAG = import.meta.env.VITE_USE_MOCK === "true";

if (GLOBAL_FLAG && import.meta.env.PROD) {
  // PROD 빌드에서 글로벌 mock이 켜져 있는 건 사고 — 발표 환경 보호.
  // eslint-disable-next-line no-console
  console.error(
    "[mock] VITE_USE_MOCK=true on PROD build. This is almost certainly a deploy mistake.",
  );
}

const DOMAIN_FLAGS: Record<string, boolean> = {
  // 도메인별 강제 mock. 백엔드 구현되면 false로 바꾸거나 키 자체를 제거.
  // (2026-05-04) 백엔드 SegmentController에 users/rebuild/campaigns 3개가 추가되어
  // mock을 false 로 전환. 백엔드 응답이 스켈레톤(빈 페이지/Stub)이지만 path는 살아있다.
  template_list: true, // 목록/상세/수정/삭제는 백엔드 미구현
  segment_users: false, // 백엔드 구현됨 (스켈레톤이지만 정상 응답)
  segment_rebuild: false, // 백엔드 구현됨 (Stub)
  segment_linked_campaigns: false, // 백엔드 구현됨
  campaign: true, // 캠페인 CRUD/실행 전체 미구현 (CampaignController 자체 없음)
  event_categories: false, // 백엔드 EventCategoryController 구현됨 (7개 endpoint)
  event_schema: false, // 동일
};

export function shouldMock(domain?: string): boolean {
  if (GLOBAL_FLAG) return true;
  if (!domain) return false;
  return DOMAIN_FLAGS[domain] ?? false;
}

export async function ifMock<T>(
  domain: string,
  mockFn: () => Promise<T> | T,
  realFn: () => Promise<T>,
): Promise<T> {
  if (shouldMock(domain)) {
    return Promise.resolve(mockFn());
  }
  return realFn();
}

/** Mock 응답에 약간의 지연을 줘 실제 네트워크처럼 보이게. */
export function delay<T>(value: T, ms = 250): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}
