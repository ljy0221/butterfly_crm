import type { Channel } from "@/schemas/campaign";

/**
 * Sprint 6 — 메시지 미리보기 패널.
 *
 * 기존 MobilePreview.jsx 의 모바일 푸시 미리보기를 .tsx 로 변환 + 채널별 분기 + 변수 강조.
 * - PUSH/SMS/KAKAO: 모바일 알림 카드 형태
 * - EMAIL: 이메일 카드 형태 (제목 + 본문)
 * - WEBHOOK: JSON-ish 코드블록
 *
 * 본문의 `{{variable}}` 패턴은 색으로 강조 (TemplateFormModal 의 VariableHighlight 와 동일 정규식).
 */

const VAR_RE = /\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}/g;

interface HighlightedSegment {
  text: string;
  variable: boolean;
}

function tokenize(body: string): HighlightedSegment[] {
  const segments: HighlightedSegment[] = [];
  let lastIndex = 0;
  let m: RegExpExecArray | null;
  // exec 상태 보존 패턴 — global flag 라 lastIndex 가 자동 갱신됨
  const re = new RegExp(VAR_RE.source, "g");
  while ((m = re.exec(body)) !== null) {
    if (m.index > lastIndex) {
      segments.push({ text: body.slice(lastIndex, m.index), variable: false });
    }
    segments.push({ text: m[0], variable: true });
    lastIndex = m.index + m[0].length;
  }
  if (lastIndex < body.length) {
    segments.push({ text: body.slice(lastIndex), variable: false });
  }
  return segments;
}

function HighlightedBody({
  body,
  size = 12,
  color = "#334155",
}: {
  body: string;
  size?: number;
  color?: string;
}) {
  const segments = tokenize(body);
  return (
    <span style={{ fontSize: size, color, lineHeight: 1.55, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
      {segments.map((seg, i) =>
        seg.variable ? (
          <span
            key={i}
            style={{
              background: "rgba(59,91,219,.12)",
              color: "#3b5bdb",
              padding: "0 3px",
              borderRadius: 3,
              fontWeight: 600,
              fontFamily: "var(--font-mono)",
              fontSize: size - 1,
            }}
          >
            {seg.text}
          </span>
        ) : (
          <span key={i}>{seg.text}</span>
        ),
      )}
    </span>
  );
}

export interface MobilePreviewPaneProps {
  channel: Channel;
  title?: string;
  body: string;
  /** 카드 외부 라벨. 기본 "메시지 미리보기". */
  heading?: string;
}

export default function MobilePreviewPane({
  channel,
  title,
  body,
  heading = "메시지 미리보기",
}: MobilePreviewPaneProps) {
  return (
    <div
      style={{
        background: "#fff",
        border: "1px solid var(--border-default, #e2e8f0)",
        borderRadius: 12,
        padding: 16,
        display: "flex",
        flexDirection: "column",
        gap: 10,
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h3 style={{ margin: 0, fontSize: 13, fontWeight: 700, color: "#1f2937" }}>{heading}</h3>
        <span style={{ fontSize: 11, color: "#94a3b8", fontWeight: 600 }}>{channelLabel(channel)}</span>
      </div>

      {channel === "EMAIL" ? (
        <EmailPreview title={title ?? "(제목 없음)"} body={body} />
      ) : channel === "WEBHOOK" ? (
        <WebhookPreview body={body} />
      ) : (
        <MobilePushPreview channel={channel} title={title} body={body} />
      )}
    </div>
  );
}

function MobilePushPreview({
  channel,
  title,
  body,
}: {
  channel: Channel;
  title?: string;
  body: string;
}) {
  const senderLabel = channel === "PUSH" ? "STUDIO SEL" : channel === "KAKAO" ? "BUTTERFLY 알림톡" : "발신자";
  return (
    <div
      style={{
        background: "linear-gradient(180deg,#e2e8f0 0%, #cbd5e1 100%)",
        borderRadius: 28,
        padding: 14,
      }}
    >
      <div
        style={{
          background: "#fff",
          borderRadius: 20,
          minHeight: 320,
          padding: "14px 12px",
          display: "flex",
          flexDirection: "column",
          gap: 10,
        }}
      >
        <div
          style={{
            fontSize: 10,
            color: "#94a3b8",
            textAlign: "center",
            fontWeight: 500,
            fontVariantNumeric: "tabular-nums",
          }}
        >
          10:00 · 월요일
        </div>
        <div
          style={{
            background: "rgba(255,255,255,.95)",
            border: "1px solid #e2e8f0",
            borderRadius: 14,
            padding: 12,
            boxShadow: "0 1px 4px rgba(0,0,0,.04)",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
            <div
              style={{
                width: 18,
                height: 18,
                borderRadius: 4,
                background: channel === "KAKAO" ? "#fbbf24" : "#3b5bdb",
                color: "#fff",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                fontSize: 10,
                fontWeight: 700,
              }}
            >
              {channel === "KAKAO" ? "K" : channel === "PUSH" ? "P" : "M"}
            </div>
            <span style={{ fontSize: 10, color: "#64748b", fontWeight: 600 }}>{senderLabel}</span>
            <span style={{ fontSize: 9, color: "#94a3b8", marginLeft: "auto" }}>지금</span>
          </div>
          {title && (
            <div style={{ fontSize: 12, fontWeight: 700, color: "#1f2937", marginBottom: 4 }}>
              <HighlightedBody body={title} size={12} color="#1f2937" />
            </div>
          )}
          <HighlightedBody body={body} />
        </div>
      </div>
    </div>
  );
}

function EmailPreview({ title, body }: { title: string; body: string }) {
  return (
    <div
      style={{
        background: "#f8fafc",
        border: "1px solid #e2e8f0",
        borderRadius: 12,
        padding: 14,
        display: "flex",
        flexDirection: "column",
        gap: 10,
      }}
    >
      <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
        <span style={{ fontSize: 10, color: "#94a3b8", fontWeight: 600 }}>제목</span>
        <span style={{ fontSize: 13, fontWeight: 700, color: "#1f2937" }}>
          <HighlightedBody body={title} size={13} color="#1f2937" />
        </span>
      </div>
      <div
        style={{
          background: "#fff",
          border: "1px solid #e2e8f0",
          borderRadius: 8,
          padding: 14,
          minHeight: 200,
        }}
      >
        <HighlightedBody body={body} size={13} />
      </div>
    </div>
  );
}

function WebhookPreview({ body }: { body: string }) {
  return (
    <pre
      style={{
        background: "#0f172a",
        color: "#e2e8f0",
        borderRadius: 8,
        padding: 14,
        fontFamily: "var(--font-mono, monospace)",
        fontSize: 12,
        lineHeight: 1.55,
        margin: 0,
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
        minHeight: 220,
      }}
    >
      <HighlightedBody body={body} size={12} color="#e2e8f0" />
    </pre>
  );
}

function channelLabel(c: Channel): string {
  switch (c) {
    case "PUSH":
      return "PUSH";
    case "EMAIL":
      return "EMAIL";
    case "SMS":
      return "SMS";
    case "WEBHOOK":
      return "WEBHOOK";
    case "KAKAO":
      return "KAKAO";
    default:
      return c;
  }
}
