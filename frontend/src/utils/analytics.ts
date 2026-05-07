/**
 * Sprint 3 — Analytics 도메인 유틸.
 *
 * 외부 라이브러리(dayjs 등) 미도입 — Asia/Seoul 타임존은 백엔드에서 처리되며,
 * 프론트는 yyyy-MM-dd 문자열만 주고받기 때문에 native Date.toISOString slice로 충분.
 */

import type { DailyPoint } from "@/api/analytics";

/** Date → "yyyy-MM-dd" (UTC 기준이지만 날짜만 쓰므로 무방) */
function toYmd(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * 오늘부터 N일 전 ~ 오늘 범위 (yyyy-MM-dd).
 * 기본 30일.
 */
export function getDefaultPeriod(days: number = 30): {
  from: string;
  to: string;
} {
  const today = new Date();
  const from = new Date(today);
  from.setDate(today.getDate() - days);
  return { from: toYmd(from), to: toYmd(today) };
}

/**
 * "yyyy-MM-dd" 두 개를 "YYYY.MM.DD – YYYY.MM.DD" 로 포맷.
 * 잘못된 입력은 원본 그대로 반환.
 */
export function formatPeriod(from: string, to: string): string {
  const fmt = (s: string): string => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
    return s.replaceAll("-", ".");
  };
  return `${fmt(from)} – ${fmt(to)}`;
}

/**
 * dailyTrend 배열 → SVG path d 문자열.
 *
 * @param trend  DailyPoint 배열 (시간순)
 * @param key    sent | delivered | clicked
 * @param width  SVG viewBox width (px)
 * @param height SVG viewBox height (px)
 * @param maxY   강제 Y 최대값. 미지정 시 trend 전체에서 자동 산출.
 *
 * 데이터 0건이면 빈 문자열을 반환 — 호출 측은 빈 상태를 별도 분기.
 */
export function dailyTrendToPath(
  trend: DailyPoint[],
  key: "sent" | "delivered" | "clicked",
  width: number,
  height: number,
  maxY?: number,
): string {
  if (!trend || trend.length === 0) return "";
  const len = trend.length;
  // 단일 포인트는 수평선으로 처리 (덴 분모 0 방지)
  const stepX = len === 1 ? 0 : width / (len - 1);
  const computedMax =
    maxY ??
    trend.reduce((acc, p) => Math.max(acc, p.sent, p.delivered, p.clicked), 0);
  const yMax = computedMax > 0 ? computedMax : 1;

  const segments: string[] = [];
  trend.forEach((p, i) => {
    const x = i * stepX;
    // 백엔드가 null/undefined를 보낼 가능성에 대비 — Number 변환 후 NaN이면 0.
    const raw = p[key];
    const v = Number.isFinite(raw) ? Number(raw) : 0;
    // y 좌표는 위에서 아래로 증가하므로 반전
    const y = height - (v / yMax) * height;
    segments.push(`${i === 0 ? "M" : "L"}${x.toFixed(2)},${y.toFixed(2)}`);
  });
  return segments.join(" ");
}

/**
 * 안전한 백분율 계산. 분모가 0/음수/NaN이면 0 반환.
 * 결과는 0~100 범위의 number (소수점 1자리 반올림은 호출 측에서).
 */
export function pct(numerator: number, denominator: number): number {
  if (!denominator || denominator <= 0 || !Number.isFinite(denominator)) {
    return 0;
  }
  if (!Number.isFinite(numerator)) return 0;
  return (numerator / denominator) * 100;
}

/**
 * 0.0 ~ 1.0 비율(분모 0이면 null) → "12.3%" 또는 "—".
 * KPI 정의서 §3 분모 0 표기 정책에 맞춤 — null/NaN/Infinity는 모두 "—".
 */
export function formatRate(
  value: number | null | undefined,
  fractionDigits = 1,
): string {
  if (value === null || value === undefined) return "—";
  if (!Number.isFinite(value)) return "—";
  return `${(value * 100).toFixed(fractionDigits)}%`;
}
