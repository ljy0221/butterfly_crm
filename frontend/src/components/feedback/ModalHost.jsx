import React from "react";
import { UIBus } from "../../lib/ui-bus";
import Button from "../common/Button.jsx";

/**
 * Modal/Confirm 호스트.
 *
 * Sprint 2.5 정합성 수정:
 *  - ConfirmOptions 키를 ui-bus.ts 정의(message / confirmText / cancelText / tone)로 일치시켰다.
 *    이전에는 desc/ok/cancel/danger 를 읽고 있어 호출부의 message·tone 이 모두 무시되고
 *    핵심 경고문이 사용자에게 노출되지 않는 보안 결함이 있었다.
 *  - openModal 두 번째 인자 ModalOptions.dismissible 을 지원한다. false 인 모달은
 *    Esc / 배경 클릭으로 닫히지 않는다 — API 키 1회 평문 노출처럼 명시적 액션이 필요한
 *    모달이 실수로 닫혀 데이터가 영구 손실되는 것을 막는다.
 */
export default function ModalHost() {
  const [modal, setModal] = React.useState(null);
  const [modalOptions, setModalOptions] = React.useState({});
  const [confirm, setConfirm] = React.useState(null);

  React.useEffect(
    () =>
      UIBus.on((e) => {
        if (e.type === "modal") {
          setModal(() => e.render);
          setModalOptions(e.options || {});
        }
        if (e.type === "confirm") setConfirm({ opts: e.opts, res: e.res });
      }),
    []
  );

  const dismissible = modalOptions.dismissible !== false;

  const handleEscape = React.useCallback(() => {
    // confirm 은 항상 dismissible — Esc 로 취소.
    setConfirm((c) => {
      if (c) c.res(false);
      return null;
    });
    if (dismissible) {
      setModal(null);
      setModalOptions({});
    }
  }, [dismissible]);

  React.useEffect(() => {
    const onKey = (e) => {
      if (e.key === "Escape") handleEscape();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [handleEscape]);

  if (!modal && !confirm) return null;

  const onBackdropClick = () => {
    setConfirm((c) => {
      if (c) c.res(false);
      return null;
    });
    if (dismissible) {
      setModal(null);
      setModalOptions({});
    }
  };

  return (
    <div
      style={{
        position: "fixed", inset: 0,
        background: "rgba(15,23,42,.40)",
        zIndex: 9000,
        display: "flex", alignItems: "center", justifyContent: "center",
        padding: 40,
        animation: "fadeIn 160ms ease-out",
      }}
      onClick={onBackdropClick}
    >
      <style>{`
        @keyframes fadeIn { from { opacity: 0 } to { opacity: 1 } }
        @keyframes popIn { from { opacity: 0; transform: scale(.96) translateY(6px) } to { opacity: 1; transform: scale(1) translateY(0) } }
      `}</style>
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          animation: "popIn 180ms cubic-bezier(.2,.7,.3,1)",
          maxHeight: "90vh",
          overflow: "auto",
        }}
      >
        {modal &&
          modal({
            close: () => {
              setModal(null);
              setModalOptions({});
            },
          })}
        {confirm && (
          <div
            style={{
              width: 420,
              background: "#fff",
              borderRadius: 14,
              boxShadow: "0 24px 64px -8px rgba(0,0,0,.25)",
              padding: 24,
            }}
          >
            <div
              style={{
                fontSize: 16, fontWeight: 700, color: "#1f2937",
                fontFamily: "var(--font-display)",
              }}
            >
              {confirm.opts.title || "확인"}
            </div>
            {confirm.opts.message && (
              <div
                style={{
                  marginTop: 8, fontSize: 13, color: "#64748b", lineHeight: 1.6,
                  whiteSpace: "pre-wrap",
                }}
              >
                {confirm.opts.message}
              </div>
            )}
            <div
              style={{
                marginTop: 20, display: "flex", gap: 8, justifyContent: "flex-end",
              }}
            >
              <Button
                variant="ghost"
                size="md"
                onClick={() => {
                  confirm.res(false);
                  setConfirm(null);
                }}
              >
                {confirm.opts.cancelText || "취소"}
              </Button>
              <Button
                variant={confirm.opts.tone === "danger" ? "primary" : "primaryStrong"}
                size="md"
                style={confirm.opts.tone === "danger" ? { background: "#dc2626" } : {}}
                onClick={() => {
                  confirm.res(true);
                  setConfirm(null);
                }}
              >
                {confirm.opts.confirmText || "확인"}
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
