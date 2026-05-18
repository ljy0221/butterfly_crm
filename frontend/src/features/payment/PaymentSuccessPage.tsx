import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Button, Card, PageHeader } from "@/components/common";
import { ApiError } from "@/api/types";
import { useConfirmPaymentMutation } from "@/hooks/queries/payment";
import type { PaymentConfirmedResponse } from "@/api/payment";

type PageState =
  | { kind: "idle" }
  | { kind: "loading" }
  | { kind: "done"; data: PaymentConfirmedResponse }
  | { kind: "error"; message: string };

/**
 * 결제 성공 콜백 페이지 (ADR-0019).
 *
 * Toss 가 결제창 완료 후 `?orderId=...&paymentKey=...&amount=...` query string 으로 redirect.
 * 본 페이지에서 백엔드 confirm 호출 → 성공 시 잔액 적립 확인, 실패 시 오류 표시.
 *
 * StrictMode 의 effect 이중 실행 + 사용자 새로고침 시 중복 호출 모두 백엔드의
 * `payment_key UNIQUE` 와 Toss `Idempotency-Key` 가 멱등하게 처리한다.
 */
export default function PaymentSuccessPage() {
  const { projectId: pidParam } = useParams<{ projectId: string }>();
  const projectId = pidParam ? Number(pidParam) : null;
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const confirmMutation = useConfirmPaymentMutation(projectId ?? -1);
  const firedRef = useRef(false);

  const [state, setState] = useState<PageState>({ kind: "idle" });

  useEffect(() => {
    if (firedRef.current || projectId == null) return;
    const orderId = params.get("orderId");
    const paymentKey = params.get("paymentKey");
    const amountStr = params.get("amount");
    if (!orderId || !paymentKey || !amountStr) {
      setState({ kind: "error", message: "결제 정보가 누락되었습니다." });
      return;
    }
    const amount = Number(amountStr);
    if (!Number.isFinite(amount)) {
      setState({ kind: "error", message: "결제 금액이 올바르지 않습니다." });
      return;
    }
    firedRef.current = true;
    setState({ kind: "loading" });
    confirmMutation
      .mutateAsync({ orderId, paymentKey, amount })
      .then((data) => setState({ kind: "done", data }))
      .catch((e) => {
        const msg =
          e instanceof ApiError
            ? e.message
            : e instanceof Error
              ? e.message
              : "결제 승인 중 오류가 발생했습니다.";
        setState({ kind: "error", message: msg });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params, projectId]);

  return (
    <div className="payment-success-page">
      <PageHeader title="결제 진행" />
      <Card>
        <div style={{ padding: 24, textAlign: "center" }}>
          {state.kind === "loading" && (
            <div style={{ fontSize: 16 }}>결제 승인 처리 중입니다...</div>
          )}
          {state.kind === "done" && (
            <>
              <div style={{ fontSize: 24, fontWeight: 600, color: "#16a34a" }}>
                충전이 완료되었습니다
              </div>
              <div style={{ marginTop: 16, fontSize: 14, color: "#666" }}>
                주문 번호: {state.data.orderId}
              </div>
              <div style={{ marginTop: 8 }}>
                결제 금액 {state.data.amount.toLocaleString()}원 ·
                결제수단 {state.data.method ?? "-"}
              </div>
              <div style={{ marginTop: 16, fontSize: 18 }}>
                현재 잔액 <strong>{state.data.balanceAfter.toLocaleString()}</strong> 크레딧
              </div>
              <div style={{ marginTop: 24 }}>
                <Button onClick={() => navigate(`/p/${projectId}/credits/charge`)}>
                  충전 화면으로
                </Button>
              </div>
            </>
          )}
          {state.kind === "error" && (
            <>
              <div style={{ fontSize: 22, fontWeight: 600, color: "#dc2626" }}>
                결제 승인 실패
              </div>
              <div style={{ marginTop: 12, color: "#666" }}>{state.message}</div>
              <div style={{ marginTop: 24 }}>
                <Button onClick={() => navigate(`/p/${projectId}/credits/charge`)}>
                  다시 시도
                </Button>
              </div>
            </>
          )}
        </div>
      </Card>
    </div>
  );
}
