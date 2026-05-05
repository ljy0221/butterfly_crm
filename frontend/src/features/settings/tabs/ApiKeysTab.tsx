import { Card, Button, Tag } from "@/components/common";
import {
  useIssueApiKeyMutation,
  useProjectQuery,
  useRevokeApiKeyMutation,
} from "@/hooks/queries/tenant";
import { useAuthStore } from "@/stores/auth";
import { confirmDialog, openModal, toast } from "@/lib/ui-bus";
import { mapTenantError } from "@/lib/tenant-errors";
import ApiKeyRevealModal from "../ApiKeyRevealModal";

/**
 * Sprint 2 — API 키 탭.
 *
 * - 백엔드 GET 미제공 → 정적 마스킹 placeholder(•) 24개로 미리보기 표시.
 * - hasApiKey boolean 으로 발급 / 재발급 모드 분기.
 * - OWNER/ADMIN 만 액션 가능. 그 외 사용자에게는 안내문 노출.
 */

const MASK = `pk_live_${"•".repeat(24)}`;

export default function ApiKeysTab({ projectId }: { projectId: number }) {
  const me = useAuthStore((s) => s.member);
  const isManager = me?.role === "OWNER" || me?.role === "ADMIN";

  const projectQ = useProjectQuery(projectId);
  const issueMut = useIssueApiKeyMutation(projectId);
  const revokeMut = useRevokeApiKeyMutation(projectId);

  const handleIssue = async () => {
    const ok = await confirmDialog({
      title: "API 키를 발급할까요?",
      message: "발급된 키는 즉시 1회만 평문으로 표시됩니다. 안전하게 저장하세요.",
      confirmText: "발급",
    });
    if (!ok) return;
    try {
      const res = await issueMut.mutateAsync();
      revealKey(res.publicApiKey);
    } catch (err) {
      const { message } = mapTenantError(err);
      toast(message, "error");
    }
  };

  const handleRevoke = async () => {
    const ok = await confirmDialog({
      title: "API 키를 재발급할까요?",
      message:
        "기존 키는 즉시 무효화되며 모든 외부 연동이 중단됩니다. 새 키는 1회만 표시됩니다.",
      confirmText: "재발급",
      tone: "danger",
    });
    if (!ok) return;
    try {
      const res = await revokeMut.mutateAsync();
      revealKey(res.publicApiKey);
    } catch (err) {
      const { message } = mapTenantError(err);
      toast(message, "error");
    }
  };

  const isLoading = projectQ.isLoading;
  const hasKey = !!projectQ.data?.hasApiKey;

  return (
    <Card title="Public API 키">
      <div
        style={{
          fontSize: 13,
          color: "#64748b",
          lineHeight: 1.6,
          marginBottom: 16,
        }}
      >
        이벤트 수집 SDK · 외부 연동에서 사용하는 공개 API 키입니다.
        <br />
        <strong style={{ color: "#b45309" }}>
          발급 직후 1회만 평문으로 표시
        </strong>
        되며 이후에는 보유 여부만 확인할 수 있습니다.
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "120px 1fr",
          rowGap: 12,
          alignItems: "center",
          fontSize: 13,
        }}
      >
        <div style={{ color: "#64748b" }}>현재 상태</div>
        <div>
          {isLoading ? (
            <div
              style={{
                width: 80,
                height: 22,
                borderRadius: 6,
                background: "#f1f5f9",
              }}
            />
          ) : hasKey ? (
            <Tag tone="success" dot>
              발급됨
            </Tag>
          ) : (
            <Tag tone="default" dot>
              미발급
            </Tag>
          )}
        </div>

        <div style={{ color: "#64748b" }}>키 미리보기</div>
        <div
          style={{
            fontFamily: "var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)",
            color: hasKey ? "#1f2937" : "#94a3b8",
            fontSize: 13,
            letterSpacing: "0.02em",
          }}
        >
          {hasKey ? MASK : "—"}
        </div>
      </div>

      <div style={{ marginTop: 24, display: "flex", gap: 8 }}>
        {isManager ? (
          hasKey ? (
            <Button
              variant="secondary"
              size="md"
              onClick={handleRevoke}
              disabled={revokeMut.isPending}
            >
              {revokeMut.isPending ? "재발급 중..." : "재발급"}
            </Button>
          ) : (
            <Button
              variant="primaryStrong"
              size="md"
              onClick={handleIssue}
              disabled={issueMut.isPending}
            >
              {issueMut.isPending ? "발급 중..." : "발급"}
            </Button>
          )
        ) : (
          <div
            style={{
              fontSize: 12,
              color: "#94a3b8",
              padding: "10px 12px",
              background: "#f8fafc",
              border: "1px solid #e2e8f0",
              borderRadius: 8,
            }}
          >
            API 키 관리는 OWNER만 수행할 수 있습니다.
          </div>
        )}
      </div>
    </Card>
  );
}

function revealKey(key: string): void {
  // dismissible:false — Esc / 배경 클릭으로 닫혀 평문이 영구 손실되는 것을 방지.
  // 사용자는 [복사하고 닫기] 버튼으로만 모달을 닫을 수 있다.
  openModal(({ close }) => <ApiKeyRevealModal apiKey={key} onClose={close} />, {
    dismissible: false,
  });
}
