import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { loadTossPayments, ANONYMOUS } from "@tosspayments/tosspayments-sdk";
import { Button, Card, PageHeader } from "@/components/common";
import { ApiError } from "@/api/types";
import { toast } from "@/lib/ui-bus";
import { CREDIT_CHARGE_TIERS, type CreditChargeTier } from "@/api/payment";
import {
  useCreatePaymentOrderMutation,
  useCreditBalanceQuery,
} from "@/hooks/queries/payment";
import { useAuthStore } from "@/stores/auth";

/**
 * 크레딧 충전 페이지 (ADR-0019).
 *
 * 흐름:
 * 1. 사용자가 정액 티어(5종) 중 하나 선택
 * 2. POST /payments/orders → 서버 발급 orderId 수신
 * 3. Toss v2 SDK 결제창 호출 → 사용자가 카드/간편결제로 결제
 * 4. Toss 가 successUrl 로 redirect — `PaymentSuccessPage` 가 confirm 처리
 */

const PAY_METHODS = [
  { id: "CARD" as const, label: "카드" },
  { id: "EASY_PAY" as const, label: "간편결제" },
];

export default function CreditChargePage() {
  const { projectId: pidParam } = useParams<{ projectId: string }>();
  const projectId = pidParam ? Number(pidParam) : null;
  const memberId = useAuthStore((s) => s.member?.memberId ?? null);
  const navigate = useNavigate();

  const [amount, setAmount] = useState<CreditChargeTier>(CREDIT_CHARGE_TIERS[0]);
  const [methodId, setMethodId] = useState<(typeof PAY_METHODS)[number]["id"]>("CARD");
  const [submitting, setSubmitting] = useState(false);

  const balanceQuery = useCreditBalanceQuery(projectId);
  const createOrder = useCreatePaymentOrderMutation(projectId ?? -1);

  const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY as string | undefined;
  const balance = balanceQuery.data;

  if (projectId == null) {
    return <div>잘못된 접근입니다.</div>;
  }

  async function handleCharge(): Promise<void> {
    if (!clientKey) {
      toast("토스 클라이언트 키가 설정되지 않았습니다 (VITE_TOSS_CLIENT_KEY).", "error");
      return;
    }
    setSubmitting(true);
    try {
      const order = await createOrder.mutateAsync({ amount });
      const tossPayments = await loadTossPayments(clientKey);
      // customerKey: 로그인 멤버 ID 문자열 — Toss 가 동일 사용자를 인식해 간편결제 등록 기억 가능.
      const customerKey = memberId != null ? String(memberId) : ANONYMOUS;
      const payment = tossPayments.payment({ customerKey });
      await payment.requestPayment({
        method: methodId,
        amount: { currency: "KRW", value: order.amount },
        orderId: order.orderId,
        orderName: `Butterfly 크레딧 ${order.amount.toLocaleString()}원 충전`,
        successUrl: `${window.location.origin}/p/${projectId}/credits/charge/success`,
        failUrl: `${window.location.origin}/p/${projectId}/credits/charge/fail`,
      });
      // requestPayment 는 결제창으로 리다이렉트되므로 이 줄 이후는 실행되지 않는다.
    } catch (e) {
      const msg =
        e instanceof ApiError
          ? e.message
          : e instanceof Error
            ? e.message
            : "결제창을 여는 중 오류가 발생했습니다.";
      toast(msg, "error");
      setSubmitting(false);
    }
  }

  return (
    <div className="credit-charge-page">
      <PageHeader title="크레딧 충전" subtitle="프로젝트 잔액을 충전합니다 (OWNER 전용)" />

      <Card>
        <div style={{ padding: 16 }}>
          <div style={{ fontSize: 13, color: "#64748b" }}>사용 가능 크레딧</div>
          <div style={{ fontSize: 28, fontWeight: 700, color: "#1f2937", fontVariantNumeric: "tabular-nums" }}>
            {balance ? balance.availableBalance.toLocaleString() : "—"} 크레딧
          </div>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
              gap: 10,
              marginTop: 14,
              fontSize: 12,
            }}
          >
            <BalanceStat label="총 보유" value={balance?.balance} />
            <BalanceStat label="예약 중" value={balance?.reservedBalance} />
          </div>
        </div>
      </Card>

      <Card style={{ marginTop: 16 }}>
        <div style={{ padding: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 600 }}>충전 금액</div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 8 }}>
            {CREDIT_CHARGE_TIERS.map((tier) => (
              <button
                key={tier}
                type="button"
                onClick={() => setAmount(tier)}
                style={{
                  padding: "12px",
                  border: amount === tier ? "2px solid #3b82f6" : "1px solid #ddd",
                  borderRadius: 8,
                  background: amount === tier ? "#eff6ff" : "#fff",
                  cursor: "pointer",
                  fontWeight: amount === tier ? 600 : 400,
                }}
              >
                {tier.toLocaleString()}원
              </button>
            ))}
          </div>

          <div style={{ marginTop: 16, marginBottom: 8, fontWeight: 600 }}>결제 수단</div>
          <div style={{ display: "flex", gap: 8 }}>
            {PAY_METHODS.map((m) => (
              <button
                key={m.id}
                type="button"
                onClick={() => setMethodId(m.id)}
                style={{
                  padding: "10px 20px",
                  border: methodId === m.id ? "2px solid #3b82f6" : "1px solid #ddd",
                  borderRadius: 8,
                  background: methodId === m.id ? "#eff6ff" : "#fff",
                  cursor: "pointer",
                }}
              >
                {m.label}
              </button>
            ))}
          </div>

          <div style={{ marginTop: 24, display: "flex", gap: 8, justifyContent: "flex-end" }}>
            <Button onClick={() => navigate(`/p/${projectId}/dashboard`)} disabled={submitting}>
              취소
            </Button>
            <Button onClick={handleCharge} disabled={submitting} variant="primary">
              {submitting ? "결제창 여는 중..." : `${amount.toLocaleString()}원 결제`}
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}

function BalanceStat({ label, value }: { label: string; value?: number }) {
  return (
    <div
      style={{
        border: "1px solid #e2e8f0",
        borderRadius: 8,
        padding: "10px 12px",
        background: "#f8fafc",
      }}
    >
      <div style={{ color: "#64748b", marginBottom: 4 }}>{label}</div>
      <div style={{ color: "#1f2937", fontWeight: 700, fontVariantNumeric: "tabular-nums" }}>
        {value != null ? value.toLocaleString() : "—"} 크레딧
      </div>
    </div>
  );
}
