# S14P31B101-294 — Grafana 캡처 절차 + 이미지 placeholder 가이드

> Claude (작업자) 는 화면 캡처 권한 없음. 사용자가 Grafana 띄워 직접 캡처.
> 단, raw 데이터는 git 에 박제되므로 캡처 실패해도 `render-graphs.py` fallback 가능.

## 1. Grafana 띄우기

```bash
docker compose -f docker-compose-local.yml -f docker-compose-observability.yml up -d
open http://localhost:3000          # macOS
# Login: admin / butterfly (anonymous viewer 도 활성, GF_AUTH_ANONYMOUS_ENABLED=true)
```

provisioning 으로 dashboard 자동 import. 좌측 메뉴 `Dashboards → Butterfly Portfolio → Campaign Send Topic Hardening`.

## 2. 표준 캡처 절차 (panel 별 동일)

1. Dashboard 열기 → 측정 시간대 (BEFORE / AFTER) 로 time range 설정
2. 비교가 필요한 경우: time range 를 한번에 다 포함 (예: 30 분 윈도우 안에 두 측정 실행)
3. 캡처 옵션 (가장 권장 순서):
   - **(A)** Panel 우상단 ⋮ → `Share → Direct link rendered image` → 자동 PNG 다운로드
   - **(B)** Panel 우상단 ⋮ → `Share → Snapshot → Local Snapshot` → JSON 저장 (raw 데이터 포함, 영구 보존)
   - **(C)** macOS 화면 캡처 `⌘+Shift+4` → PNG 영역 캡처 (수동)
4. 파일명 표준: `<phase>-<id>-<short-desc>.png`
   - 예: `baseline-throughput.png`, `h3-partition-concurrency-throughput.png`, `h7-dlq-resilience.png`
5. 저장 위치: `docs/portfolio-graphs/images/`

## 3. 포트폴리오 문서 안 이미지 placeholder 표준 형식

포트폴리오 문서 (`PORTFOLIO-1-Throughput.md` 등) 에 이미지 자리는 다음 형식으로 박제:

```markdown
### 📊 [GRAPH-PLACEHOLDER #B-1] H3 적용 전후 throughput 비교

- **저장 경로**: `docs/portfolio-graphs/images/graph-B-1-h3-throughput.png`
- **데이터 source**: Grafana `Campaign Send Topic Hardening` → panel `Consumer message rate`
- **Prometheus query**: `rate(spring_kafka_listener_seconds_count{topic="campaign.send.requested"}[10s])`
- **시간 범위**: BEFORE 측정 시간대 + H3 측정 시간대 동시 표시
- **차트 타입**: time-series line, dual series (BEFORE / AFTER 색 구분)
- **캡처 절차**: (위 §2 표준 절차 따름)
- **Raw 백업**: 
  - K6: `baseline-results/h3-partition-concurrency/baseline-raw-*.csv`
  - Prom: `snapshots/h3-*-kafka_listener_rate.json`
- **Fallback 자동 PNG**: `render-graphs.py` 가 위 raw 로 동일 패널 자동 생성 가능
```

→ 이미지 깨져도 raw 와 Fallback 명령으로 100% 복원.

## 4. Snapshot (Local) 활용 — 강력 추천

Grafana 의 **Local Snapshot** 은 panel 의 정의 + 그 시점 raw 데이터 둘 다 JSON 으로 박제.
나중에 dashboard 가 깨져도, snapshot JSON 만 import 하면 view-only 로 같은 그래프 그대로 복원.

```
panel 우상단 ⋮ → Share → Snapshot tab → "Local Snapshot" 클릭
→ 결과 URL: http://localhost:3000/dashboard/snapshot/abc123
→ JSON 다운로드: 우상단 메뉴 → Export → Save to file
→ docs/portfolio-graphs/snapshots/ 에 저장
```

git 추적되니 사라지지 않음.

## 5. 측정 시퀀스 (각 가설마다 반복)

```
[A] application 측 config 변경 (예: H3 → partition=8 + concurrency=4)
[B] ./gradlew :butterfly-api:bootRun (Spring app 재기동, application context 적용)
[C] (선택) 토픽 partition 재생성 — kafka-topics.sh 직접
[D] scripts/measure/run-baseline.sh 실행 (자동 K6 + Prom snapshot + render)
[E] Grafana UI 에서 측정 시간대 캡처 (위 §2 절차)
[F] 결과 파일을 가설별 디렉토리로 정리 (예: baseline-results/h3-partition-concurrency/)
[G] 포트폴리오 문서에 수치 + 이미지 박제
[H] commit + push
```

## 6. 주의사항

- **5초 scrape 주기** — Prometheus 가 5초 간격으로 데이터 가져옴. 30초 미만 짧은 측정은 시계열 점이 적어 차트가 거칠 수 있음. **권장 측정 시간: 60초 이상**.
- **histogram quantile** — p95/p99 같은 quantile 은 `histogram_quantile()` PromQL 사용. instant query 가 아니라 `rate(...[1m])` window 가 누적된 후에야 의미 있음.
- **JVM warmup** — Spring app 부팅 직후 JIT 최적화 안 끝남. 측정 전 60초 정도 idle hit 권장.
- **재현성** — 가설별 측정은 같은 hardware / 같은 시간대에 수행해야 비교 가능. 실측 후 환경 변수 (CPU/mem 사용량) 도 한 번 캡처.
