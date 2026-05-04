import { Link } from "react-router-dom";

export default function NotFound() {
  return (
    <div
      style={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        background: "#f5f7fb",
      }}
    >
      <h1 style={{ fontSize: 48, margin: 0, color: "#1f2937" }}>404</h1>
      <p style={{ color: "#64748b", margin: "8px 0 24px" }}>
        요청하신 페이지를 찾을 수 없습니다.
      </p>
      <Link
        to="/login"
        style={{
          padding: "10px 16px",
          background: "#3b5bdb",
          color: "#fff",
          borderRadius: 8,
          textDecoration: "none",
          fontSize: 14,
        }}
      >
        로그인으로 돌아가기
      </Link>
    </div>
  );
}
