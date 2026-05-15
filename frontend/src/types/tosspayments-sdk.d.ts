/**
 * `@tosspayments/tosspayments-sdk` ambient 타입 선언 (ADR-0019).
 *
 * 본 PR 시점 패키지 dependency 만 추가하고 `npm install` 은 사용자 환경에서 실행한다.
 * 그 사이 type-check 통과를 위해 가벼운 ambient 선언을 둔다. `npm install` 후 본 파일은
 * 제거하거나 그대로 둬도 패키지가 가진 타입이 우선 적용된다.
 */
declare module "@tosspayments/tosspayments-sdk" {
  export const ANONYMOUS: string;

  export interface RequestPaymentParams {
    method: string;
    amount: { currency: string; value: number };
    orderId: string;
    orderName: string;
    successUrl: string;
    failUrl: string;
    customerEmail?: string;
    customerName?: string;
  }

  export interface Payment {
    requestPayment(params: RequestPaymentParams): Promise<void>;
  }

  export interface TossPayments {
    payment(opts: { customerKey: string }): Payment;
  }

  export function loadTossPayments(clientKey: string): Promise<TossPayments>;
}
