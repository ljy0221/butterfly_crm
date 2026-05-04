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
 */

const GLOBAL_FLAG = import.meta.env.VITE_USE_MOCK === "true";

const DOMAIN_FLAGS: Record<string, boolean> = {
  // 도메인별 강제 mock. 백엔드 구현되면 false로 바꾸거나 키 자체를 제거.
  // (2026-05-04) 백엔드 SegmentController에 users/rebuild/campaigns 3개가 추가되어
  // mock을 false 로 전환. 백엔드 응답이 스켈레톤(빈 페이지/Stub)이지만 path는 살아있다.
  template_list: true, // 목록/상세/수정/삭제는 백엔드 미구현
  segment_users: false, // 백엔드 구현됨 (스켈레톤이지만 정상 응답)
  segment_rebuild: false, // 백엔드 구현됨 (Stub)
  segment_linked_campaigns: false, // 백엔드 구현됨
  campaign: true, // 캠페인 CRUD/실행 전체 미구현 (CampaignController 자체 없음)
  event_categories: true, // 이벤트 카테고리 CRUD 미구현
  event_schema: true,
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
