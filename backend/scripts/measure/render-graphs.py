#!/usr/bin/env python3
"""
S14P31B101-294 — CSV → PNG fallback 렌더링.

Grafana 대시보드 캡처 실패 시 또는 자동화된 baseline/AFTER 비교용.
K6 CSV (raw samples) 와 Prometheus snapshot JSON 을 입력으로 받아 matplotlib 로 PNG 생성.

사용:
  python3 render-graphs.py --input baseline-raw.csv --output ./images/auto-baseline --label "Baseline"
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from collections import defaultdict
from pathlib import Path

try:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
except ImportError:
    sys.stderr.write("[ERROR] matplotlib 미설치. pip install matplotlib\n")
    sys.exit(1)


def parse_k6_csv(path: Path) -> dict[str, list[tuple[float, float]]]:
    """K6 CSV 파싱: metric_name → [(timestamp, value), ...]"""
    series: dict[str, list[tuple[float, float]]] = defaultdict(list)
    if not path.exists():
        return series
    with open(path, newline="") as f:
        reader = csv.DictReader(f)
        for row in reader:
            metric = row.get("metric_name", "")
            ts = float(row.get("timestamp", 0))
            try:
                val = float(row.get("metric_value", 0))
            except ValueError:
                continue
            series[metric].append((ts, val))
    return series


def render_k6_metric(metric: str, samples: list[tuple[float, float]], out_path: Path, label: str):
    if not samples:
        return
    samples.sort()
    ts = [s[0] - samples[0][0] for s in samples]
    vals = [s[1] for s in samples]
    fig, ax = plt.subplots(figsize=(10, 4))
    ax.plot(ts, vals, linewidth=0.8, label=label)
    ax.set_title(f"{metric} — {label}")
    ax.set_xlabel("time (s)")
    ax.set_ylabel(metric)
    ax.grid(True, alpha=0.3)
    ax.legend()
    fig.tight_layout()
    fig.savefig(out_path, dpi=120)
    plt.close(fig)
    print(f"  ✓ {out_path}")


def render_prom_snapshot(snapshot_dir: Path, out_dir: Path, label: str):
    """Prometheus query_range JSON 들을 PNG 로 렌더."""
    if not snapshot_dir.exists():
        return
    out_dir.mkdir(parents=True, exist_ok=True)
    for json_file in sorted(snapshot_dir.glob("*.json")):
        try:
            data = json.loads(json_file.read_text())
        except json.JSONDecodeError:
            continue
        result = data.get("data", {}).get("result", [])
        if not result:
            continue
        fig, ax = plt.subplots(figsize=(10, 4))
        for series in result:
            metric = series.get("metric", {})
            values = series.get("values", [])
            if not values:
                continue
            ts = [float(v[0]) - float(values[0][0]) for v in values]
            ys = [float(v[1]) for v in values]
            legend = ",".join(f"{k}={v}" for k, v in metric.items()) or "value"
            ax.plot(ts, ys, linewidth=0.8, label=legend)
        ax.set_title(f"{json_file.stem} — {label}")
        ax.set_xlabel("time (s)")
        ax.grid(True, alpha=0.3)
        if len(result) <= 5:
            ax.legend(loc="best", fontsize=8)
        fig.tight_layout()
        png_path = out_dir / f"{json_file.stem}.png"
        fig.savefig(png_path, dpi=120)
        plt.close(fig)
        print(f"  ✓ {png_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="K6 raw CSV 경로")
    parser.add_argument("--output", required=True, help="출력 prefix (디렉토리 prefix)")
    parser.add_argument("--label", default="run", help="그래프 라벨")
    parser.add_argument("--prom-snapshot", help="Prometheus snapshot JSON 디렉토리")
    args = parser.parse_args()

    out_dir = Path(args.output).parent
    out_prefix = Path(args.output).name
    out_dir.mkdir(parents=True, exist_ok=True)

    # K6 CSV 렌더
    csv_path = Path(args.input)
    print(f"[1] K6 CSV 렌더: {csv_path}")
    series = parse_k6_csv(csv_path)
    for metric, samples in series.items():
        png_path = out_dir / f"{out_prefix}-{metric}.png"
        render_k6_metric(metric, samples, png_path, args.label)

    # Prometheus snapshot 렌더 (선택)
    if args.prom_snapshot:
        snapshot_dir = Path(args.prom_snapshot)
        print(f"[2] Prometheus snapshot 렌더: {snapshot_dir}")
        render_prom_snapshot(snapshot_dir, out_dir / f"{out_prefix}-prom", args.label)

    print("✅ render 완료")


if __name__ == "__main__":
    main()
