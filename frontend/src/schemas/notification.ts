import { z } from "zod";

/**
 * Sprint 7 — Notification 도메인 Zod 스키마.
 *
 * 백엔드:
 *  - DeliveryCommand.Channel: PUSH | EMAIL | KAKAO | SMS | WEBHOOK
 *  - NotificationStatus: SENT | DELIVERED | CLICKED | FAILED
 *  - DeliveryErrorCode: DELIVERY_001~019
 */

export const NotificationChannelSchema = z.enum([
  "PUSH",
  "EMAIL",
  "SMS",
  "KAKAO",
  "WEBHOOK",
]);
export type NotificationChannel = z.infer<typeof NotificationChannelSchema>;

export const NotificationStatusSchema = z.enum([
  "SENT",
  "DELIVERED",
  "FAILED",
  "CLICKED",
]);
export type NotificationStatus = z.infer<typeof NotificationStatusSchema>;

/** ChannelCredentialController 가 PathVariable 로 받는 채널 (FCM 한정 일부 채널만 지원) */
export const CredentialChannelSchema = z.enum([
  "PUSH",
  "EMAIL",
  "SMS",
  "WEBHOOK",
]);
export type CredentialChannel = z.infer<typeof CredentialChannelSchema>;

export const credentialSaveSchema = z.object({
  credentialData: z.string().min(1, "자격증명 데이터는 필수입니다"),
});
export type CredentialSaveInput = z.infer<typeof credentialSaveSchema>;

export const fcmSendSchema = z.object({
  credentialJson: z.string().min(1),
  tokens: z.array(z.string().min(1)).min(1, "발송 대상 토큰이 비어 있습니다"),
  title: z.string().max(200).optional(),
  body: z.string().max(1000).optional(),
  imageUrl: z.string().url().optional(),
});
export type FcmSendInput = z.infer<typeof fcmSendSchema>;
