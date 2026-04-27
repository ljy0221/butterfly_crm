import React from "react";

const SIZES = {
  xs: { height: 24, padding: "0 8px", fontSize: 11, radius: 6 },
  sm: { height: 28, padding: "0 10px", fontSize: 12, radius: 6 },
  md: { height: 34, padding: "0 14px", fontSize: 13, radius: 8 },
  lg: { height: 40, padding: "0 18px", fontSize: 14, radius: 8 },
};

const VARIANTS = {
  primary:       { bg: "#2563eb",     color: "#fff",     border: "transparent", hover: "#1d4ed8" },
  primaryStrong: { bg: "#3b5bdb",     color: "#fff",     border: "transparent", hover: "#2b49c4" },
  secondary:     { bg: "#fff",        color: "#1f2937",  border: "#cbd5e1",     hover: "#f8fafc" },
  tertiary:      { bg: "#f1f5f9",     color: "#1f2937",  border: "transparent", hover: "#e2e8f0" },
  text:          { bg: "transparent", color: "#2563eb",  border: "transparent", hover: "#eff3fe" },
  ghost:         { bg: "transparent", color: "#64748b",  border: "#e2e8f0",     hover: "#f8fafc" },
  danger:        { bg: "#fff",        color: "#b91c1c",  border: "#fecaca",     hover: "#fef2f2" },
};

export default function Button({
  variant = "primary", size = "md",
  children, icon, iconRight, style, ...rest
}) {
  const s = SIZES[size];
  const v = VARIANTS[variant];
  const [hover, setHover] = React.useState(false);

  return (
    <button
      {...rest}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      style={{
        height: s.height, padding: s.padding, fontSize: s.fontSize,
        borderRadius: s.radius,
        background: hover ? v.hover : v.bg, color: v.color,
        border: `1px solid ${v.border === "transparent" ? "transparent" : v.border}`,
        fontWeight: 600, fontFamily: "inherit",
        cursor: "pointer", display: "inline-flex", alignItems: "center", gap: 6,
        transition: "background 120ms",
        whiteSpace: "nowrap",
        ...style,
      }}
    >
      {icon}
      {children}
      {iconRight}
    </button>
  );
}
