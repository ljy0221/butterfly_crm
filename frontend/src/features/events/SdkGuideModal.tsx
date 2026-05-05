import { useState } from "react";
import { Button, Icon } from "@/components/common";
import { toast } from "@/lib/ui-bus";
import { useProjectStore } from "@/stores/project";
import { useProjectQuery } from "@/hooks/queries/tenant";

/**
 * Sprint 7 — SDK 설치 가이드 모달.
 *
 * Sprint 2 useProjectQuery 의 hasApiKey 로 키 발급 여부만 알 수 있다 (실제 평문 키는 발급/재발급
 * 시점 1회만 노출됨). 따라서 발급되어 있으면 마스킹 placeholder 를, 미발급이면 안내 문구를 보여준다.
 */

interface Props {
  close: () => void;
}

type Tab = "javascript" | "android" | "ios";

export default function SdkGuideModal({ close }: Props) {
  const [tab, setTab] = useState<Tab>("javascript");
  const project = useProjectStore((s) => s.current);
  const projectId = project?.projectId ?? null;
  const { data: tenantProject } = useProjectQuery(projectId, projectId != null);

  const hasKey = !!tenantProject?.hasApiKey;
  const apiKeyPlaceholder = hasKey
    ? `pk_live_${"•".repeat(24)}`
    : "pk_live_(API 키 발급 필요)";

  const snippets: Record<Tab, string> = {
    javascript: `import Butterfly from '@butterfly/web-sdk';

Butterfly.init({
  publicKey: '${apiKeyPlaceholder}',
});

Butterfly.track('purchase', {
  order_id: 'SO-2026-041829',
  total_amount: 184000,
  is_first_order: true,
});`,
    android: `// build.gradle
implementation 'com.butterfly.crm:sdk-android:1.4.2'

// Application.kt
Butterfly.init(this, "${apiKeyPlaceholder}")
Butterfly.track("purchase", mapOf(
    "order_id" to "SO-2026-041829",
    "total_amount" to 184000
))`,
    ios: `import ButterflySDK

Butterfly.initialize(publicKey: "${apiKeyPlaceholder}")
Butterfly.track("purchase", properties: [
    "order_id": "SO-2026-041829",
    "total_amount": 184000
])`,
  };

  const onCopy = (): void => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(snippets[tab]);
      toast("코드를 복사했습니다", "success");
    }
  };

  return (
    <div
      style={{
        width: 640,
        maxWidth: "92vw",
        background: "#fff",
        borderRadius: 14,
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
        overflow: "hidden",
      }}
    >
      <div
        style={{
          padding: "18px 22px",
          borderBottom: "1px solid #f1f5f9",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <div>
          <div
            style={{
              fontSize: 17,
              fontWeight: 700,
              color: "#1f2937",
              fontFamily: "var(--font-display)",
            }}
          >
            SDK 설치 가이드
          </div>
          <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>
            Public API 키를 사용해 이벤트를 수집하세요. Secret Key 는 서버에만 사용합니다.
          </div>
        </div>
        <button
          type="button"
          aria-label="닫기"
          onClick={close}
          style={{
            border: 0,
            background: "transparent",
            color: "#94a3b8",
            cursor: "pointer",
          }}
        >
          {Icon.x(16)}
        </button>
      </div>
      <div style={{ padding: 22 }}>
        <div
          style={{
            display: "flex",
            gap: 2,
            marginBottom: 14,
            borderBottom: "1px solid #e2e8f0",
          }}
          role="tablist"
          aria-label="플랫폼"
        >
          {(
            [
              ["javascript", "Web · JavaScript"],
              ["android", "Android"],
              ["ios", "iOS"],
            ] as Array<[Tab, string]>
          ).map(([k, l]) => {
            const active = tab === k;
            return (
              <button
                key={k}
                role="tab"
                aria-selected={active}
                onClick={() => setTab(k)}
                style={{
                  padding: "8px 14px",
                  fontSize: 12,
                  fontWeight: 500,
                  border: 0,
                  background: "transparent",
                  color: active ? "#1f2937" : "#64748b",
                  fontFamily: "inherit",
                  borderBottom: active
                    ? "2px solid #3b5bdb"
                    : "2px solid transparent",
                  marginBottom: -1,
                  cursor: "pointer",
                }}
              >
                {l}
              </button>
            );
          })}
        </div>

        <div style={{ position: "relative" }}>
          <pre
            style={{
              margin: 0,
              padding: 16,
              background: "#0f172a",
              color: "#e2e8f0",
              borderRadius: 10,
              fontFamily: "var(--font-mono)",
              fontSize: 12,
              lineHeight: 1.6,
              overflow: "auto",
              whiteSpace: "pre-wrap",
            }}
          >
            {snippets[tab]}
          </pre>
          <button
            type="button"
            onClick={onCopy}
            style={{
              position: "absolute",
              top: 10,
              right: 10,
              height: 26,
              padding: "0 10px",
              fontSize: 11,
              fontWeight: 600,
              border: 0,
              borderRadius: 6,
              background: "rgba(255,255,255,.12)",
              color: "#fff",
              cursor: "pointer",
              display: "inline-flex",
              alignItems: "center",
              gap: 4,
              fontFamily: "inherit",
            }}
          >
            {Icon.copy(11)} 복사
          </button>
        </div>

        <div
          style={{
            marginTop: 14,
            padding: 12,
            background: "#f8fafc",
            borderRadius: 8,
            fontSize: 12,
            color: "#64748b",
            lineHeight: 1.7,
          }}
        >
          <div style={{ fontWeight: 600, color: "#1f2937", marginBottom: 2 }}>
            💡 이벤트 카테고리 먼저 등록해주세요
          </div>
          플랫폼에 등록되지 않은 이벤트는 수집되지 않습니다. 기본 제공:{" "}
          <code style={{ fontFamily: "var(--font-mono)", color: "#3b5bdb" }}>
            purchase · add_to_cart · page_view · login · signup
          </code>
          {!hasKey && (
            <div
              style={{
                marginTop: 8,
                color: "#b45309",
                fontSize: 11,
              }}
            >
              ⚠ 아직 Public API 키가 발급되지 않았습니다. [설정 ▸ API 키] 에서 발급 후
              SDK 에 입력하세요.
            </div>
          )}
        </div>
      </div>
      <div
        style={{
          padding: "12px 22px",
          borderTop: "1px solid #f1f5f9",
          display: "flex",
          justifyContent: "flex-end",
          gap: 8,
        }}
      >
        <Button variant="ghost" size="md" onClick={close} style={{}}>
          닫기
        </Button>
      </div>
    </div>
  );
}
