import { useLayoutEffect, useRef, type ChangeEvent } from "react";

/**
 * `{{variable}}` 패턴을 색으로 강조하는 textarea 오버레이.
 *
 * 구현 전략:
 *  - 동일한 `<pre>` 오버레이를 textarea 와 같은 폰트/사이즈/패딩/lineHeight로 깔고
 *    textarea는 투명한 caret 색만 보이도록 텍스트 색을 비운다.
 *  - 스크롤 동기화는 textarea 의 onScroll → pre 의 scrollTop/scrollLeft 갱신.
 *  - `\{\{(\w+)\}\}` 매칭은 백엔드와 동일.
 *
 * 접근성:
 *  - aria-describedby 가 있으면 그대로 textarea 에 패스. 부모가 "감지된 변수: ..." 로 안내.
 */

export interface VariableHighlightProps {
  value: string;
  onChange: (next: string) => void;
  rows?: number;
  maxLength?: number;
  placeholder?: string;
  ariaLabel?: string;
  ariaDescribedBy?: string;
  id?: string;
  invalid?: boolean;
}

// 백엔드 MessageTemplateVariableExtractor와 동일한 패턴
//   - 변수 식별자: 영문/언더스코어로 시작 + 영문/숫자/언더스코어
//   - {{ 와 }} 사이 공백 허용
const VAR_RE = /\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}/g;

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function highlight(value: string): string {
  // 마지막 개행이 화면에서 잘리지 않도록 trailing newline 보호용 스페이스 추가
  const safe = value.endsWith("\n") ? value + " " : value;
  return escapeHtml(safe).replace(
    VAR_RE,
    (_, name: string) =>
      `<span style="background:rgba(59,91,219,.12);color:#3b5bdb;border-radius:3px">{{${escapeHtml(name)}}}</span>`,
  );
}

const FONT_FAMILY = "var(--font-mono, ui-monospace, SFMono-Regular, Menlo, monospace)";
const PADDING = 12;
const FONT_SIZE = 13;
const LINE_HEIGHT = 1.6;

export default function VariableHighlight({
  value,
  onChange,
  rows = 8,
  maxLength = 1000,
  placeholder,
  ariaLabel,
  ariaDescribedBy,
  id,
  invalid = false,
}: VariableHighlightProps) {
  const taRef = useRef<HTMLTextAreaElement>(null);
  const preRef = useRef<HTMLPreElement>(null);
  // 마지막으로 저장된 커서 위치 — onChange → useLayoutEffect 사이에 보존
  const selRef = useRef<[number, number]>([0, 0]);
  // IME 조합 중(한글 등)에는 setSelectionRange 호출 금지
  const composingRef = useRef(false);

  useLayoutEffect(() => {
    const ta = taRef.current;
    const pre = preRef.current;
    if (!ta || !pre) return;
    // 커서 복원: React가 textarea.value를 DOM에 쓸 때 커서를 잃는 버그 방지
    if (!composingRef.current && document.activeElement === ta) {
      ta.setSelectionRange(selRef.current[0], selRef.current[1]);
    }
    // 오버레이 스크롤 동기화
    pre.scrollTop = ta.scrollTop;
    pre.scrollLeft = ta.scrollLeft;
  }, [value]);

  const onScroll = (): void => {
    const ta = taRef.current;
    const pre = preRef.current;
    if (ta && pre) {
      pre.scrollTop = ta.scrollTop;
      pre.scrollLeft = ta.scrollLeft;
    }
  };

  const onTextChange = (e: ChangeEvent<HTMLTextAreaElement>): void => {
    selRef.current = [
      e.target.selectionStart ?? 0,
      e.target.selectionEnd ?? 0,
    ];
    onChange(e.target.value);
  };

  const sharedStyle: React.CSSProperties = {
    margin: 0,
    width: "100%",
    minHeight: rows * (FONT_SIZE * LINE_HEIGHT) + PADDING * 2,
    padding: PADDING,
    fontFamily: FONT_FAMILY,
    fontSize: FONT_SIZE,
    lineHeight: LINE_HEIGHT,
    whiteSpace: "pre-wrap",
    wordBreak: "break-word",
    overflowWrap: "break-word",
    boxSizing: "border-box",
    border: "1px solid",
    borderColor: invalid ? "#ef4444" : "#e2e8f0",
    borderRadius: 8,
    background: "#fff",
  };

  return (
    <div
      style={{
        position: "relative",
        width: "100%",
      }}
    >
      <pre
        ref={preRef}
        aria-hidden="true"
        style={{
          ...sharedStyle,
          position: "absolute",
          inset: 0,
          color: "#1f2937",
          background: "#fff",
          pointerEvents: "none",
          overflow: "hidden",
        }}
        // 변수 매칭 결과를 dangerouslySet로 주입. value 는 zod 검증 후 사용자 입력 텍스트만 거치며
        // escapeHtml 로 sanitize 한다.
        dangerouslySetInnerHTML={{ __html: highlight(value) }}
      />
      <textarea
        id={id}
        ref={taRef}
        value={value}
        onChange={onTextChange}
        onScroll={onScroll}
        onCompositionStart={() => { composingRef.current = true; }}
        onCompositionEnd={() => { composingRef.current = false; }}
        rows={rows}
        maxLength={maxLength}
        placeholder={placeholder}
        aria-label={ariaLabel}
        aria-describedby={ariaDescribedBy}
        spellCheck={false}
        style={{
          ...sharedStyle,
          position: "relative",
          color: "transparent",
          caretColor: "#1f2937",
          background: "transparent",
          resize: "vertical",
          outline: "none",
        }}
      />
    </div>
  );
}
