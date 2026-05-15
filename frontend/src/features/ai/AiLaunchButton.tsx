import { Icon } from "@/components/common";

interface AiLaunchButtonProps {
  label: string;
  caption?: string;
  onClick: () => void;
}

export default function AiLaunchButton({
  label,
  caption = "Copilot",
  onClick,
}: AiLaunchButtonProps) {
  return (
    <>
      <style>{`
        @keyframes aiLaunchAura {
          0%, 100% { transform: scale(1); opacity: .52; }
          50% { transform: scale(1.08); opacity: .88; }
        }
        @keyframes aiLaunchSweep {
          0% { transform: translateX(-180%) skewX(-18deg); opacity: 0; }
          12% { opacity: .85; }
          58% { opacity: .85; }
          78%, 100% { transform: translateX(260%) skewX(-18deg); opacity: 0; }
        }
        @keyframes aiLaunchFloat {
          0%, 100% { transform: translateY(0) scale(1); }
          50% { transform: translateY(-1px) scale(1.01); }
        }
        @keyframes aiLaunchIconPulse {
          0%, 100% { transform: scale(1); box-shadow: inset 0 0 0 1px rgba(255,255,255,.24); }
          50% { transform: scale(1.06); box-shadow: inset 0 0 0 1px rgba(255,255,255,.38), 0 0 18px rgba(255,255,255,.24); }
        }
      `}</style>
      <button
        type="button"
        onClick={onClick}
        style={{
          position: "relative",
          isolation: "isolate",
          display: "inline-flex",
          alignItems: "center",
          gap: 10,
          minHeight: 42,
          padding: "0 16px 0 13px",
          border: "1px solid rgba(99, 102, 241, .46)",
          borderRadius: 999,
          color: "#fff",
          background:
            "linear-gradient(135deg, #1d4ed8 0%, #4f46e5 46%, #db2777 100%)",
          boxShadow:
            "0 14px 34px -18px rgba(79, 70, 229, .95), inset 0 1px 0 rgba(255,255,255,.28)",
          cursor: "pointer",
          overflow: "hidden",
          animation: "aiLaunchFloat 4.8s ease-in-out infinite",
        }}
        aria-label={label}
      >
        <span
          aria-hidden="true"
          style={{
            position: "absolute",
            inset: -8,
            zIndex: -1,
            borderRadius: 999,
            background:
              "radial-gradient(circle at 18% 20%, rgba(255,255,255,.72), transparent 24%), radial-gradient(circle at 86% 40%, rgba(244,114,182,.58), transparent 26%)",
            filter: "blur(10px)",
            animation: "aiLaunchAura 4.8s ease-in-out infinite",
          }}
        />
        <span
          aria-hidden="true"
          style={{
            position: "absolute",
            top: -6,
            bottom: -6,
            left: 0,
            width: 54,
            background:
              "linear-gradient(90deg, transparent, rgba(255,255,255,.72), transparent)",
            animation: "aiLaunchSweep 3.8s linear infinite",
          }}
        />
        <span
          aria-hidden="true"
          style={{
            display: "inline-grid",
            placeItems: "center",
            width: 24,
            height: 24,
            borderRadius: 999,
            background: "rgba(255,255,255,.18)",
            boxShadow: "inset 0 0 0 1px rgba(255,255,255,.24)",
            animation: "aiLaunchIconPulse 3.6s ease-in-out infinite",
          }}
        >
          {Icon.events(15)}
        </span>
        <span style={{ display: "grid", gap: 1, textAlign: "left" }}>
          <span style={{ fontSize: 12, fontWeight: 800, lineHeight: 1 }}>
            {label}
          </span>
          <span
            style={{
              fontSize: 10,
              fontWeight: 700,
              lineHeight: 1,
              color: "rgba(255,255,255,.78)",
              letterSpacing: 0,
            }}
          >
            {caption}
          </span>
        </span>
      </button>
    </>
  );
}
