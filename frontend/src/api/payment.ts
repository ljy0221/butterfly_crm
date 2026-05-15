import { api } from "./client";

/**
 * 결제 API 클라이언트 (ADR-0019).
 *
 * 백엔드 컨트롤러:
 * - com.b101.butterfly.payment.presentation.PaymentController
 * - com.b101.butterfly.payment.presentation.CreditController
 *
 * 베이스 경로: `/api/v1/projects/{projectId}/{payments|credits}`
 * 모든 엔드포인트 OWNER 권한 필요.
 */

// ---------- 상수 ----------

export const CREDIT_CHARGE_TIERS = [10_000, 30_000, 50_000, 100_000, 300_000] as const;
export type CreditChargeTier = (typeof CREDIT_CHARGE_TIERS)[number];

export type PaymentStatus =
  | "PENDING"
  | "CONFIRMED"
  | "FAILED"
  | "CANCELLED"
  | "PARTIAL_CANCELLED";

export type PaymentMethod =
  | "CARD"
  | "EASY_PAY"
  | "VIRTUAL_ACCOUNT"
  | "MOBILE_PHONE"
  | "TRANSFER"
  | "GIFT_CERTIFICATE";

export type CreditReason = "CHARGE" | "REFUND" | "SPEND" | "ADJUST";

// ---------- 응답 ----------

export interface PaymentOrderCreateResponse {
  orderId: string;
  amount: number;
}

export interface PaymentConfirmedResponse {
  orderId: string;
  status: PaymentStatus;
  method: PaymentMethod | null;
  amount: number;
  balanceAfter: number;
}

export interface CreditBalanceResponse {
  projectId: number;
  balance: number;
}

export interface CreditTransactionItem {
  creditTransactionId: number;
  paymentOrderId: number | null;
  delta: number;
  reason: CreditReason;
  balanceAfter: number;
  memo: string | null;
  createdAt: string;
}

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// ---------- 엔드포인트 ----------

/** POST /api/v1/projects/{projectId}/payments/orders — OWNER */
export async function createPaymentOrder(
  projectId: number,
  amount: CreditChargeTier,
): Promise<PaymentOrderCreateResponse> {
  const res = await api.post<PaymentOrderCreateResponse>(
    `/api/v1/projects/${projectId}/payments/orders`,
    { amount },
  );
  return res.data;
}

/** POST /api/v1/projects/{projectId}/payments/confirm — OWNER */
export async function confirmPayment(
  projectId: number,
  body: { orderId: string; paymentKey: string; amount: number },
): Promise<PaymentConfirmedResponse> {
  const res = await api.post<PaymentConfirmedResponse>(
    `/api/v1/projects/${projectId}/payments/confirm`,
    body,
  );
  return res.data;
}

/** GET /api/v1/projects/{projectId}/credits/balance — OWNER */
export async function getCreditBalance(
  projectId: number,
): Promise<CreditBalanceResponse> {
  const res = await api.get<CreditBalanceResponse>(
    `/api/v1/projects/${projectId}/credits/balance`,
  );
  return res.data;
}

/** GET /api/v1/projects/{projectId}/credits/transactions — OWNER */
export async function listCreditTransactions(
  projectId: number,
  params: { page?: number; size?: number } = {},
): Promise<PageResponse<CreditTransactionItem>> {
  const res = await api.get<PageResponse<CreditTransactionItem>>(
    `/api/v1/projects/${projectId}/credits/transactions`,
    { params },
  );
  return res.data;
}
