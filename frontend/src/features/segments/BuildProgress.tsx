import { useEffect, useState } from "react";

/**
 * Segment 빌드 진행률 표시 (S14P31B101-275).
 *
 * - target 이 있으면 (current/target)*100% 게이지 + 숫자 표시
 * - target 이 null 이면 indeterminate (진행 단계가 서버에서 분리되지 않은 작업)
 * - startedAt 이 있으면 경과 시간 ("Xm Ys") 표시
 */
interface BuildProgressProps {
  current: number;
  target: number | null;
  startedAt: string | null;
}

const fmt = (n: number) => n.toLocaleString();

function formatElapsed(seconds: number): string {
  if (seconds < 60) return `${seconds}초`;
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}분 ${s}초`;
}

export default function BuildProgress({
  current,
  target,
  startedAt,
}: BuildProgressProps) {
  // 경과 시간은 startedAt 부터 매초 갱신.
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const elapsedSec = startedAt
    ? Math.max(0, Math.floor((now - new Date(startedAt).getTime()) / 1000))
    : null;

  const pct =
    target != null && target > 0
      ? Math.min(100, Math.floor((current / target) * 100))
      : null;

  return (
    <div style={{ marginBottom: 12 }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          fontSize: 12,
          color: "#475569",
          marginBottom: 6,
          fontVariantNumeric: "tabular-nums",
        }}
      >
        <span>
          {target != null && target > 0
            ? `처리됨 ${fmt(current)} / ${fmt(target)}`
            : "재계산 진행 중"}
          {pct != null ? ` · ${pct}%` : ""}
        </span>
        {elapsedSec != null && <span>경과 {formatElapsed(elapsedSec)}</span>}
      </div>
      <div
        style={{
          height: 6,
          width: "100%",
          background: "#e2e8f0",
          borderRadius: 4,
          overflow: "hidden",
        }}
      >
        {pct != null ? (
          // determinate bar
          <div
            style={{
              height: "100%",
              width: `${pct}%`,
              background: "#3b82f6",
              transition: "width 300ms ease-out",
            }}
          />
        ) : (
          // indeterminate stripes (target 모를 때)
          <div
            style={{
              height: "100%",
              width: "30%",
              background:
                "linear-gradient(90deg, transparent 0%, #3b82f6 50%, transparent 100%)",
              animation: "buildProgressShimmer 1.4s infinite linear",
            }}
          />
        )}
      </div>
      <style>{`
        @keyframes buildProgressShimmer {
          0%   { transform: translateX(-100%); }
          100% { transform: translateX(380%); }
        }
      `}</style>
    </div>
  );
}
