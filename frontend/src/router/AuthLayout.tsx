import { Outlet } from "react-router-dom";
import { ToastHost, ModalHost } from "@/components/feedback";

/**
 * 인증/공개 페이지(로그인, 회원가입, 신청서, 오너 비밀번호 설정) 풀스크린 레이아웃.
 */
export default function AuthLayout() {
  return (
    <div
      style={{
        minHeight: "100vh",
        background: "linear-gradient(180deg,#f5f7fb 0%,#e2e8f0 100%)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
      }}
    >
      <Outlet />
      <ToastHost />
      <ModalHost />
    </div>
  );
}
