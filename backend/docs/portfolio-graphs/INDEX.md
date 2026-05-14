# S14P31B101-294 — 포트폴리오 그래프 자료 INDEX

> Kafka 토픽 `campaign.send.requested` 의 처리량/복원력/지연 측정 자료.
> 본 폴더의 raw 데이터는 git 으로 영구 보존 — 이미지 깨져도 100% 복원 가능.

## 디렉토리 구조

```
docs/portfolio-graphs/
├── INDEX.md                                  # 본 파일 (인덱스 + 복원 가이드)
├── CAPTURE-GUIDE.md                          # Grafana 캡처 절차
├── campaign-send-hardening-dashboard.json    # Grafana dashboard 정의
├── baseline-results/                         # ★ K6 raw 결과 (가설별)
│   ├── baseline-raw-*.json                   #   k6 모든 sample (시계열 원본)
│   ├── baseline-raw-*.csv                    #   k6 CSV (Excel/Pandas)
│   ├── baseline-summary.json                 #   k6 통계 요약
│   ├── h1-partition-only/                    #   가설별 동일 구조 반복
│   ├── h2-concurrency-only/
│   ├── h3-partition-concurrency/
│   ├── h4-processor-parallel/
│   ├── h5-manual-ack/
│   ├── ...
├── snapshots/                                # ★ Prometheus 시계열 raw (가설별)
│   ├── baseline-*-kafka_listener_rate.json
│   ├── baseline-*-kafka_listener_p95.json
│   └── ...
└── images/                                   # 사용자 캡처 PNG (또는 render-graphs.py 자동)
    ├── auto-baseline-*-*.png                 #   render-graphs.py 가 만든 fallback
    ├── manual-baseline-throughput.png        #   사용자가 Grafana 에서 캡처
    └── ...
```

## 4중 데이터 보존 전략

| 백업 층 | 형식 | 위치 | 복원 가능성 |
|---|---|---|---|
| **1. K6 raw** | JSON + CSV | `baseline-results/*.{json,csv}` | 모든 sample raw — 어떤 도구로도 그래프 다시 그림 |
| **2. K6 summary** | JSON (통계) | `baseline-results/*-summary.json` | p50/p95/p99/avg/min/max 박제 — 표 자동 생성 가능 |
| **3. Prometheus snapshot** | JSON (시계열) | `snapshots/*.json` | scrape 결과 보존 — Prometheus 컨테이너 사라져도 OK |
| **4. PNG fallback** | PNG (시각) | `images/auto-*.png` | render-graphs.py 가 1+2+3 으로 자동 생성 |

## 복원 절차 (이미지 깨짐 / Grafana 사라짐 시)

```bash
# A) render-graphs.py 로 자동 PNG 재생성
python3 ../scripts/measure/render-graphs.py \
  --input baseline-results/baseline-raw-20260514-153000.csv \
  --prom-snapshot snapshots/ \
  --output images/restore-baseline \
  --label "Baseline (BEFORE)"

# B) Excel/Google Sheets — CSV 열어 직접 차트 작성
open baseline-results/baseline-raw-*.csv

# C) Python notebook — pandas + matplotlib
python3 -c "
import pandas as pd
df = pd.read_csv('baseline-results/baseline-raw-20260514-153000.csv')
print(df.groupby(['scenario','metric_name'])['metric_value'].describe())
"

# D) Grafana 다시 띄우기 + dashboard 재import
docker compose -f docker-compose-observability.yml up -d grafana
# Grafana UI → Dashboards → Import → campaign-send-hardening-dashboard.json
```

## 가설별 측정 결과 인덱스

| 가설 ID | 시나리오 | raw 결과 디렉토리 | 측정 일시 | 비고 |
|---|---|---|---|---|
| Baseline | B+E | `baseline-results/` | (BEFORE 측정 후 채움) | partition=1, concurrency=1, AUTO ack |
| H1 | B | `baseline-results/h1-partition-only/` | TBD | partition=8, concurrency=1 |
| H2 | B | `baseline-results/h2-concurrency-only/` | TBD | partition=1, concurrency=4 |
| H3 | B | `baseline-results/h3-partition-concurrency/` | TBD | partition=8, concurrency=4 |
| H4 | B+E | `baseline-results/h4-processor-parallel/` | TBD | H3 + Processor 내부 병렬화 |
| H5 | C | `baseline-results/h5-manual-ack/` | TBD | Manual ACK only |
| H6 | C | `baseline-results/h6-retry-3/` | TBD | Manual ACK + FixedBackOff(3) |
| H7 | C | `baseline-results/h7-dlq/` | TBD | H6 + DLQ topic |
| H8 | E | `baseline-results/h8-lookup-parallel/` | TBD | template/segment 병렬 + contact/tags 병렬 |
| H9 | E | `baseline-results/h9-producer-async/` | TBD | Producer .get() 제거 + callback |
| H10 | E | `baseline-results/h10-h8-h9/` | TBD | H8 + H9 조합 |
