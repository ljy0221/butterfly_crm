// =============================================================================
// scripts/qa/k6/common.js
//
// k6 시나리오 공통 헬퍼 — base URL, payload 생성기, 공통 헤더.
// 환경 변수로 설정 주입 (CI 와 로컬 동일하게 동작):
//
//   BASE_URL            기본 http://localhost:8080
//   API_KEY             SDK Public API Key (pk_live_xxx... 32자) — 시드 스크립트에서 발급
//   LOG_ID              부모 notification_logs.id — 시드된 row
//   PROJECT_ID          시드된 project_id (LOG_ID 의 소속)
//   USER_ID             시드된 user_id
//   DEVICE_ID           시드된 device_id
//
// 시드 절차: scripts/qa/seed/README.md
// 관련 문서: backend/docs/qa/03-load-and-failure-scenarios.md
// 관련 ADR:  backend/docs/adr/0017-public-api-key-authentication.md
// =============================================================================

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const API_KEY = __ENV.API_KEY || 'pk_live_test0000000000000000abcd';
export const LOG_ID = parseInt(__ENV.LOG_ID || '42', 10);
export const PROJECT_ID = parseInt(__ENV.PROJECT_ID || '1', 10);
export const USER_ID = parseInt(__ENV.USER_ID || '1', 10);
export const DEVICE_ID = parseInt(__ENV.DEVICE_ID || '1', 10);

/**
 * 정상 사용자 반응 이벤트 payload 생성.
 * actionType 은 OPEN/CLICK/CONVERT/DISMISS 중 무작위 선택.
 */
export function buildActionPayload(overrides = {}) {
  const types = ['OPEN', 'CLICK', 'CONVERT', 'DISMISS'];
  const actionType = overrides.actionType || types[Math.floor(Math.random() * types.length)];
  const payload = {
    logId: overrides.logId !== undefined ? overrides.logId : LOG_ID,
    userId: overrides.userId !== undefined ? overrides.userId : USER_ID,
    deviceId: overrides.deviceId !== undefined ? overrides.deviceId : DEVICE_ID,
    actionType,
    actionTarget: actionType === 'CLICK' ? 'https://shop.example.com/promo' : undefined,
    userAgent: 'k6/load-test',
    actionAt: new Date().toISOString().replace('Z', ''), // LocalDateTime 호환 (오프셋 제거)
  };
  return JSON.stringify(payload);
}

/**
 * X-API-Key 헤더 포함 공통 헤더.
 */
export function authHeaders(extra = {}) {
  return Object.assign(
    {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
    },
    extra,
  );
}

/**
 * 인증 없는 헤더 (헤더 누락 401 검증용).
 */
export function noAuthHeaders(extra = {}) {
  return Object.assign({ 'Content-Type': 'application/json' }, extra);
}
