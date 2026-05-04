import { Link } from "react-router-dom";

interface Props {
  title: string;
  domain: string;
  sprint: string;
  description?: string;
}

/**
 * Sprint 진행 전 도메인의 임시 페이지.
 * 각 스프린트가 시작되면 해당 도메인의 실제 페이지(.tsx)로 교체된다.
 */
export default function Placeholder({ title, domain, sprint, description }: Props) {
  return (
    <div
      style={{
        maxWidth: 720,
        margin: "60px auto",
        textAlign: "center",
        background: "#fff",
        border: "1px solid #e2e8f0",
        borderRadius: 12,
        padding: "48px 32px",
        boxShadow: "0 1px 4px 0 rgba(0,0,0,0.04)",
      }}
    >
      <div
        style={{
          display: "inline-block",
          background: "rgba(59,91,219,0.10)",
          color: "#3b5bdb",
          fontSize: 12,
          fontWeight: 500,
          padding: "4px 10px",
          borderRadius: 999,
          marginBottom: 16,
          letterSpacing: "0.02em",
        }}
      >
        {sprint} · {domain}
      </div>
      <h1 style={{ marginTop: 0, marginBottom: 8, fontSize: 28 }}>{title}</h1>
      <p style={{ color: "#64748b", margin: "0 0 24px", lineHeight: 1.6 }}>
        {description ??
          "이 도메인은 아직 구현되지 않았습니다. 4-에이전트 스프린트에서 곧 채워질 예정입니다."}
      </p>
      <div style={{ display: "flex", justifyContent: "center", gap: 12 }}>
        <Link
          to="/projects"
          style={{
            padding: "10px 16px",
            border: "1px solid #e2e8f0",
            borderRadius: 8,
            color: "#334155",
            textDecoration: "none",
            fontSize: 14,
          }}
        >
          ← 프로젝트 선택
        </Link>
      </div>
    </div>
  );
}
