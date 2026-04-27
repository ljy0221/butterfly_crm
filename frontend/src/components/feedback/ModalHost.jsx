import React from "react";
import { UIBus } from "../../lib/ui-bus.js";
import Button from "../common/Button.jsx";

export default function ModalHost() {
  const [modal, setModal] = React.useState(null);
  const [confirm, setConfirm] = React.useState(null);

  React.useEffect(
    () =>
      UIBus.on((e) => {
        if (e.type === "modal") setModal(() => e.render);
        if (e.type === "confirm") setConfirm({ opts: e.opts, res: e.res });
      }),
    []
  );

  React.useEffect(() => {
    const onKey = (e) => {
      if (e.key === "Escape") {
        setModal(null);
        setConfirm((c) => {
          if (c) c.res(false);
          return null;
        });
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!modal && !confirm) return null;

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
      onClick={() => {
        setModal(null);
        setConfirm((c) => {
          if (c) c.res(false);
          return null;
        });
      }}
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
        {modal && modal({ close: () => setModal(null) })}
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
            {confirm.opts.desc && (
              <div
                style={{
                  marginTop: 8, fontSize: 13, color: "#64748b", lineHeight: 1.6,
                }}
              >
                {confirm.opts.desc}
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
                {confirm.opts.cancel || "취소"}
              </Button>
              <Button
                variant={confirm.opts.danger ? "primary" : "primaryStrong"}
                size="md"
                style={confirm.opts.danger ? { background: "#dc2626" } : {}}
                onClick={() => {
                  confirm.res(true);
                  setConfirm(null);
                }}
              >
                {confirm.opts.ok || "확인"}
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
