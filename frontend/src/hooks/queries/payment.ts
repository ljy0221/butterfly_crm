import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import * as paymentApi from "@/api/payment";
import type {
  CreditBalanceResponse,
  CreditChargeTier,
  CreditTransactionItem,
  PaymentConfirmedResponse,
  PaymentOrderCreateResponse,
} from "@/api/payment";

/**
 * 결제·크레딧 React Query 훅 (ADR-0019).
 *
 * 잔액·원장은 OWNER 전용이라 fetch 가 403 으로 떨어질 수 있다. 컴포넌트 단에서 role 가드와
 * 함께 사용한다.
 */

export const paymentKeys = {
  balance: (projectId: number) => ["credits", projectId, "balance"] as const,
  transactions: (projectId: number, page: number, size: number) =>
    ["credits", projectId, "transactions", page, size] as const,
};

// ---------- Queries ----------

export function useCreditBalanceQuery(
  projectId: number | null,
  enabled: boolean = true,
) {
  return useQuery<CreditBalanceResponse>({
    queryKey: paymentKeys.balance(projectId ?? -1),
    queryFn: () => paymentApi.getCreditBalance(projectId as number),
    enabled: enabled && projectId != null,
  });
}

interface TransactionsPage {
  content: CreditTransactionItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function useCreditTransactionsQuery(
  projectId: number | null,
  params: { page: number; size: number },
  enabled: boolean = true,
) {
  return useQuery<TransactionsPage>({
    queryKey: paymentKeys.transactions(projectId ?? -1, params.page, params.size),
    queryFn: () => paymentApi.listCreditTransactions(projectId as number, params),
    enabled: enabled && projectId != null,
  });
}

// ---------- Mutations ----------

export function useCreatePaymentOrderMutation(projectId: number) {
  return useMutation<
    PaymentOrderCreateResponse,
    Error,
    { amount: CreditChargeTier }
  >({
    mutationFn: ({ amount }) => paymentApi.createPaymentOrder(projectId, amount),
  });
}

export function useConfirmPaymentMutation(projectId: number) {
  const qc = useQueryClient();
  return useMutation<
    PaymentConfirmedResponse,
    Error,
    { orderId: string; paymentKey: string; amount: number }
  >({
    mutationFn: (body) => paymentApi.confirmPayment(projectId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: paymentKeys.balance(projectId) });
      qc.invalidateQueries({
        queryKey: ["credits", projectId, "transactions"],
      });
    },
  });
}
