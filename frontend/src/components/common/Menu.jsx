import React from "react";

export default function Menu({ trigger, items, align = "right" }) {
  const [open, setOpen] = React.useState(false);
  const ref = React.useRef(null);

  React.useEffect(() => {
    const onDoc = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener("mousedown", onDoc);
    return () => document.removeEventListener("mousedown", onDoc);
  }, []);

  return (
    <span ref={ref} style={{ position: "relative", display: "inline-block" }}>
      <span onClick={(e) => { e.stopPropagation(); setOpen((o) => !o); }}>{trigger}</span>
      {open && (
        <div
          style={{
            position: "absolute",
            top: "calc(100% + 4px)",
            [align]: 0,
            zIndex: 500,
            background: "#fff",
            border: "1px solid #e2e8f0",
            borderRadius: 8,
            boxShadow: "0 8px 32px -8px rgba(0,0,0,.15)",
            minWidth: 160, padding: 4,
          }}
        >
          {items.map((it, i) =>
            it.divider ? (
              <div key={i} style={{ height: 1, background: "#f1f5f9", margin: "4px 0" }} />
            ) : (
              <button
                key={i}
                onClick={() => { setOpen(false); it.onClick && it.onClick(); }}
                style={{
                  display: "flex", alignItems: "center", gap: 8, width: "100%",
                  padding: "7px 10px", background: "transparent",
                  border: 0, borderRadius: 6,
                  fontSize: 12, fontFamily: "inherit",
                  color: it.danger ? "#b91c1c" : "#334155",
                  cursor: "pointer", textAlign: "left",
                }}
                onMouseEnter={(e) => (e.currentTarget.style.background = it.danger ? "#fef2f2" : "#f8fafc")}
                onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
              >
                {it.icon && (
                  <span style={{ color: it.danger ? "#b91c1c" : "#64748b" }}>{it.icon}</span>
                )}
                {it.label}
              </button>
            )
          )}
        </div>
      )}
    </span>
  );
}
