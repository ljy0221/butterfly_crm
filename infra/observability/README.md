# Butterfly Observability — 발급 EC2 LGTM 서버

> ADR-0018 (모니터링 agent로 node-exporter+Promtail 유지, Alloy 미도입) 결정의
> **Phase 1 산출물**. AWS K3s 측 수집 agent(node-exporter + Promtail DaemonSet)는
> Phase 2(K8s 클러스터 프로비저닝 후)에서 별도 작업.

## 토폴로지

```text
[K8s 4 노드 + Promtail/node-exporter (Phase 2)]
         │
         │ HTTPS (Basic Auth: promtail-client)
         ▼
[발급 EC2 = k14b101.p.ssafy.io]
  Caddy (TLS 종료, LE 자동 갱신)
    ├ /lgtm/loki/*    → loki:3100   (Promtail push 수신)
    ├ /lgtm/prom/*    → prometheus:9090 (remote_write 수신 + scrape)
    ├ /lgtm/alerts/*  → alertmanager:9093 (admin Basic Auth)
    └ /grafana/*      → grafana:3000 (admin Basic Auth + Grafana 자체 로그인)
  prometheus → loki (datasource), alertmanager (alert routing)
```

## 부팅 절차

### 1. 호스트 준비 (발급 EC2)

```bash
# Docker / Compose v2 설치 (Ubuntu 22.04)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER  # 재로그인 필요

# UFW 인바운드
sudo ufw allow 80/tcp           # ACME challenge + redirect
sudo ufw allow 443/tcp          # 본 서비스 (Caddy)
# 9090/3100/3000 직접 노출 X — Caddy만 진입점
```

### 2. 환경 변수 채우기

```bash
cd infra/observability
cp .env.example .env

# Basic Auth 해시 생성 (admin / promtail-client 각각)
docker run --rm caddy:2.8-alpine caddy hash-password --plaintext '운영자비번'
docker run --rm caddy:2.8-alpine caddy hash-password --plaintext 'agent비번'

# ⚠️ 출력된 bcrypt 해시(`$2a$14$xxxx...`)의 모든 `$` 를 `$$` 로 escape 해서 .env 에 붙여넣기.
# docker-compose 가 .env 의 `$` 를 변수 보간 문자로 해석하기 때문 (sed 활용):
#   caddy hash-password --plaintext 'pwd' | sed 's/\$/$$/g'

# .env 의 PLACEHOLDER 모두 치환
vim .env
```

### 3. 부팅

```bash
docker compose up -d
docker compose ps              # 5 컨테이너 (caddy/prometheus/loki/grafana/alertmanager) Up
docker compose logs -f caddy   # LE 발급 진행 (~30초)

# Caddy 발급 실패 시 staging 모드로 검증 후 production:
# Caddyfile 의 acme_ca 라인 활성화 → docker compose restart caddy
```

### 4. 동작 검증

```bash
# Grafana (운영자 UI) — admin Basic Auth + Grafana 자체 로그인 2단계
curl -u admin:'운영자비번' https://k14b101.p.ssafy.io/grafana/api/health
# {"database": "ok", ...}

# Prometheus 자기 메트릭 수집 확인
curl -u admin:'운영자비번' https://k14b101.p.ssafy.io/lgtm/alerts/api/v2/status
# Alertmanager status JSON
```

## 운영 절차

### Loki 디스크 가득

```bash
# 현재 사용량
docker exec butterfly-obs-loki du -sh /loki

# 강제 retention 적용 (compactor가 비동기 GC)
docker exec butterfly-obs-loki ls /loki/compactor

# 임계 도달 시 retention_period 축소 (loki-config.yml: 720h → 360h)
vim loki/loki-config.yml
docker compose restart loki
```

### Prometheus rule reload (재시작 없이)

```bash
# rules/ 변경 후
docker exec butterfly-obs-prometheus killall -HUP prometheus
# 또는 lifecycle API
curl -X POST http://localhost:9090/-/reload
```

### LE 인증서 갱신

Caddy가 60일마다 자동. 수동 강제는:

```bash
docker compose restart caddy
```

### 백업

```bash
# 컨테이너 정지 후 볼륨 dump (데이터 손실 방지 목적)
docker compose down
docker run --rm -v butterfly-obs_prometheus-data:/data -v $(pwd):/backup ubuntu \
  tar czf /backup/prometheus-data-$(date +%Y%m%d).tar.gz /data
docker run --rm -v butterfly-obs_loki-data:/data -v $(pwd):/backup ubuntu \
  tar czf /backup/loki-data-$(date +%Y%m%d).tar.gz /data
docker compose up -d
```

## 장애 대응

| 증상 | 1차 진단 | 조치 |
| --- | --- | --- |
| Grafana 502 | `docker compose ps grafana` | restart, 안 되면 `logs grafana` |
| Promtail push 401 | `.env` BASIC_AUTH_PROMTAIL_HASH 확인 | hash 재생성 + Promtail Secret 갱신 |
| LE 발급 실패 (rate limit) | `docker compose logs caddy` | `acme_ca` staging로 전환 후 디버그 |
| Loki 쿼리 느림 | `du -sh /loki/chunks` | 라벨 cardinality 폭발 의심 — `topk` 쿼리로 카디널리티 확인 |
| Prometheus scrape 실패 (kubernetes-nodes) | NodePort 30090 도달 가능? | AWS sg-worker 에 발급 EC2 IP/32 inbound 확인 |

## 후속 작업 (Phase 2~)

- **Phase 2**: AWS K3s monitoring namespace manifest (node-exporter + promtail DaemonSet)
- **Phase 3**: Grafana 대시보드 JSON (도메인별: api 응답성능 / 분석 집계 SLO / 멀티테넌트 기준)
- **Phase 4**: Alertmanager 알람 규칙 (SLO 위반 기준)
- **Phase 5**: Tempo + 분산 트레이싱 (별도 ADR)

## 참고

- ADR-0018: `backend/docs/adr/0018-monitoring-agent-deferred-alloy.md`
- log-policy: `backend/docs/operations/log-policy.md` (필수 라벨/마스킹/보존)
- K8s plan v2: `docs/dev-journey/2026-05-11-k8s-deploy-plan.md`
- 메모리: `project_observability_stack.md` (cardinality 금지 라벨 5종)
