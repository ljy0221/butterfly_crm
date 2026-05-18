import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Button, Card, PageHeader } from "@/components/common";

/**
 * 결제 실패 콜백 페이지 (ADR-0019).
 *
 * Toss 가 실패 시 `?code=...&message=...&orderId=...` query string 으로 redirect.
 * 백엔드 호출 없이 사용자에게 사유만 표시 — 주문은 PENDING 으로 남고 운영자 보정 또는
 * 사용자 재시도 시 새 주문이 발급된다.
 */
export default function PaymentFailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const code = params.get("code");
  const message = params.get("message");
  const orderId = params.get("orderId");

  return (
    <div className="payment-fail-page">
      <PageHeader title="결제 실패" />
      <Card>
        <div style={{ padding: 24, textAlign: "center" }}>
          <div style={{ fontSize: 22, fontWeight: 600, color: "#dc2626" }}>
            결제가 완료되지 않았습니다
          </div>
          <div style={{ marginTop: 16, color: "#666" }}>
            {message ?? "사용자가 결제를 취소했거나 오류가 발생했습니다."}
          </div>
          {code && (
            <div style={{ marginTop: 8, fontSize: 13, color: "#999" }}>코드: {code}</div>
          )}
          {orderId && (
            <div style={{ marginTop: 4, fontSize: 13, color: "#999" }}>
              주문번호: {orderId}
            </div>
          )}
          <div style={{ marginTop: 24 }}>
            <Button onClick={() => navigate(`/p/${projectId}/credits/charge`)}>
              다시 시도
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}
