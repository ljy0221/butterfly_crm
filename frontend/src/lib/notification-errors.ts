import { ApiError } from "@/api/types";

/**
 * Sprint 7 — Notification(Delivery) 도메인 에러 코드 → UI 메시지 매핑.
 *
 * 백엔드 enum:
 *  c:/ssafy/S14P31B101/backend/butterfly-notification/.../DeliveryErrorCode.java
 *  - DELIVERY_001 ~ DELIVERY_019
 */

export interface NotificationErrorMapping {
  field?: string;
  message: string;
  display: "field" | "toast" | "banner";
}

const MAPPING: Record<string, NotificationErrorMapping> = {
  DELIVERY_001: { message: "발송 로그를 찾을 수 없습니다", display: "banner" },
  DELIVERY_002: { message: "유효하지 않은 상태 전이입니다", display: "toast" },
  DELIVERY_003: { message: "FCM 발송에 실패했습니다", display: "toast" },
  DELIVERY_004: {
    message: "FCM 자격증명이 설정되지 않았습니다",
    display: "banner",
  },
  DELIVERY_005: { message: "지원하지 않는 발송 채널입니다", display: "toast" },
  DELIVERY_006: { message: "캠페인 통계를 찾을 수 없습니다", display: "banner" },
  DELIVERY_007: {
    field: "tokens",
    message: "발송 대상이 비어 있습니다",
    display: "field",
  },
  DELIVERY_008: {
    field: "credentialJson",
    message: "FCM 자격증명이 유효하지 않습니다",
    display: "field",
  },
  DELIVERY_009: { message: "이메일 발송에 실패했습니다", display: "toast" },
  DELIVERY_010: { message: "유효하지 않은 이메일 주소입니다", display: "toast" },
  DELIVERY_011: { message: "SMS 발송에 실패했습니다", display: "toast" },
  DELIVERY_012: { message: "유효하지 않은 전화번호입니다", display: "toast" },
  DELIVERY_013: { message: "Webhook 발송에 실패했습니다", display: "toast" },
  DELIVERY_014: { message: "유효하지 않은 Webhook URL입니다", display: "toast" },
  DELIVERY_015: { message: "Push 토큰이 누락되었습니다", display: "toast" },
  DELIVERY_016: { message: "이메일 주소가 누락되었습니다", display: "toast" },
  DELIVERY_017: { message: "전화번호가 누락되었습니다", display: "toast" },
  DELIVERY_018: { message: "Webhook URL이 누락되었습니다", display: "toast" },
  DELIVERY_019: { message: "채널 인증정보를 찾을 수 없습니다", display: "banner" },
};

export function mapNotificationError(err: unknown): NotificationErrorMapping {
  if (err instanceof ApiError) {
    const code = err.code ?? "";
    const hit = MAPPING[code];
    if (hit) return hit;
    return {
      message: err.message ?? "요청 처리 중 오류가 발생했습니다",
      display: "toast",
    };
  }
  const message =
    err instanceof Error ? err.message : "일시적인 오류가 발생했습니다";
  return { message, display: "toast" };
}
