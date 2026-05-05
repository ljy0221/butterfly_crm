import type { NotificationStatus } from "@/schemas/notification";

/**
 * Sprint 7 — Notification 상태/채널 라벨 + Tag tone 매핑.
 */

export type TagTone =
  | "default"
  | "success"
  | "warning"
  | "error"
  | "info"
  | "dark"
  | "soft";

export const STATUS_LABEL: Record<NotificationStatus, string> = {
  SENT: "발송",
  DELIVERED: "전달",
  CLICKED: "클릭",
  FAILED: "실패",
};

export const STATUS_TONE: Record<NotificationStatus, TagTone> = {
  SENT: "default",
  DELIVERED: "success",
  CLICKED: "info",
  FAILED: "error",
};

export const CHANNEL_LABEL: Record<string, string> = {
  PUSH: "PUSH",
  EMAIL: "EMAIL",
  SMS: "SMS",
  KAKAO: "KAKAO",
  WEBHOOK: "WEBHOOK",
};

/** errorCode 표시용. DELIVERY_xxx 코드를 더 짧게 보여주기. */
export function shortErrorCode(code: string | null): string {
  if (!code) return "";
  return code.replace(/^DELIVERY_/, "");
}

/** 수신자 마스킹 — 토큰/이메일/전화 형태에 따라. */
export function maskRecipient(
  recipient: string | null,
  channel: string | null,
): string {
  if (!recipient) return "-";
  const r = recipient.trim();
  if (!r) return "-";
  if (channel === "EMAIL") {
    const at = r.indexOf("@");
    if (at < 0) return r;
    const local = r.slice(0, at);
    const dom = r.slice(at);
    if (local.length <= 2) return `${local[0] ?? "*"}*${dom}`;
    return `${local.slice(0, 2)}${"*".repeat(Math.max(2, local.length - 2))}${dom}`;
  }
  if (channel === "SMS") {
    if (r.length <= 4) return r;
    return `${r.slice(0, 3)}-****-${r.slice(-4)}`;
  }
  // PUSH 토큰 등
  if (r.length <= 12) return r;
  return `${r.slice(0, 8)}…${r.slice(-4)}`;
}
