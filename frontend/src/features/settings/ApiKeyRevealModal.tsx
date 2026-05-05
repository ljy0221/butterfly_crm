import { useEffect, useRef, useState } from "react";
import { Button, Icon } from "@/components/common";
import { toast } from "@/lib/ui-bus";

/**
 * Sprint 2 — API 키 발급/재발급 1회 평문 노출 모달.
 *
 * 결정 사항(Frontend Agent 노트):
 *  - ModalHost 코드 수정 없이 처리.
 *  - 컨텐츠 div에 onClick stopPropagation은 ModalHost가 이미 적용 중이므로
 *    "배경 클릭 닫힘"은 그대로 동작한다. 이 도메인 UX 강도상 사용자가 명시적
 *    [복사하고 닫기] 버튼을 누르도록 권장하되, ModalHost 변경은 후속 스프린트로 미룸.
 *  - 키 메모리 정리: 컴포넌트 unmount 시 ref/state를 즉시 비움.
 *  - 키 영역: monospace + autoFocus + select() — Cmd/Ctrl+C 즉시 복사 가능.
 *
 * Props.apiKey 는 부모에서 mutation 응답으로 받아서 1회 전달한 평문 문자열.
 */
export interface ApiKeyRevealModalProps {
  apiKey: string;
  onClose: () => void;
}

export default function ApiKeyRevealModal({
  apiKey,
  onClose,
}: ApiKeyRevealModalProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const node = inputRef.current;
    // 마운트 직후 키 입력란 focus + 전체 선택
    requestAnimationFrame(() => {
      node?.focus();
      node?.select();
    });
    // unmount 시 입력 값을 즉시 비워 메모리에서 제거
    return () => {
      if (node) node.value = "";
    };
  }, []);

  const onCopy = async () => {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(apiKey);
      } else {
        // Fallback: legacy execCommand
        inputRef.current?.select();
        document.execCommand("copy");
      }
      setCopied(true);
      toast("API 키를 복사했습니다", "success");
    } catch {
      toast("복사에 실패했습니다. 직접 선택해 복사해주세요.", "error");
    }
  };

  const onCopyAndClose = async () => {
    await onCopy();
    onClose();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="api-key-reveal-title"
      style={{
        background: "#fff",
        borderRadius: 14,
        padding: 24,
        width: 520,
        maxWidth: "90vw",
        boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
      }}
    >
      <h2
        id="api-key-reveal-title"
        style={{
          margin: 0,
          fontSize: 16,
          fontWeight: 700,
          color: "#1f2937",
          fontFamily: "var(--font-display)",
        }}
      >
        API 키가 발급되었습니다
      </h2>

      <div
        role="alert"
        style={{
          marginTop: 12,
          padding: 12,
          background: "rgba(245,158,11,.10)",
          border: "1px solid rgba(245,158,11,.30)",
          borderRadius: 8,
          color: "#b45309",
          fontSize: 12.5,
          lineHeight: 1.6,
        }}
      >
        ⚠️ 이 화면을 닫으면 키 평문은 다시 볼 수 없습니다. 안전한 곳에 저장하세요.
      </div>

      <div
        style={{
          marginTop: 16,
          display: "flex",
          gap: 8,
          alignItems: "stretch",
        }}
      >
        <input
          ref={inputRef}
          readOnly
          defaultValue={apiKey}
          onFocus={(e) => e.currentTarget.select()}
          aria-label="발급된 API 키"
          style={{
            flex: 1,
            height: 40,
            padding: "0 12px",
            fontFamily: "var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)",
            fontSize: 14,
            color: "#0f172a",
            background: "#0f172a08",
            border: "1px solid #cbd5e1",
            borderRadius: 8,
            outline: "none",
            letterSpacing: "0.02em",
          }}
        />
        <button
          type="button"
          onClick={onCopy}
          aria-label="API 키 복사"
          title="복사"
          style={{
            width: 44,
            border: "1px solid #cbd5e1",
            borderRadius: 8,
            background: copied ? "#dcfce7" : "#fff",
            color: copied ? "#15803d" : "#475569",
            cursor: "pointer",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          {copied ? Icon.check(16) : Icon.copy(16)}
        </button>
      </div>

      <div
        style={{
          marginTop: 20,
          display: "flex",
          justifyContent: "flex-end",
          gap: 8,
        }}
      >
        <Button variant="primaryStrong" size="md" onClick={onCopyAndClose}>
          복사하고 닫기
        </Button>
      </div>
    </div>
  );
}
