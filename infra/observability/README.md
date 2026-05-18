# Butterfly Observability — 발급 EC2 LGTM 서버

> ADR-0018 (모니터링 agent로 node-exporter+Promtail 유지, Alloy 미도입) 결정의
> **Phase 1 산출물**.

## 토폴로지

```text
[K8s 5 노드 — node-exporter DaemonSet NodePort 30090]
         │
         │ scrape (Prometheus pull)
         ▼
[발급 EC2 = k14b101.p.ssafy.io]
  nginx (TLS 종료, Let's Encrypt, host 직접 설치)
    ├ /api/*       → 15.165.115.142:30080  (butterfly-api)
    ├ /actuator/*  → 15.165.115.142:30080  (Spring Boot Actuator)
    ├ /argo/*      → 43.202.126.218:30888  (ArgoCD UI)
    ├ /grafana/*   → 127.0.0.1:3000        (Grafana Docker)
    └ /loki/*      → 127.0.0.1:3100        (Loki Docker, Promtail push)

  Docker 컨테이너 (127.0.0.1 바인드, nginx 경유만 외부 접근):
    prometheus:9090  — node-exporter pull + butterfly-api actuator pull
    loki:3100        — 로그 수집
    grafana:3000     — 대시보드 UI
    node-exporter    — 로그 서버 자체 메트릭 (host network)
```

## K8s 노드 EIP 매핑

| 노드 | Internal IP | EIP | 역할 |
|------|------------|-----|------|
| ip-10-0-1-144 | 10.0.1.144 | 3.34.240.94 | master (control-plane) |
| ip-10-0-1-231 | 10.0.1.231 | 15.165.115.142 | worker-1 (butterfly-api, kafka) |
| ip-10-0-2-56  | 10.0.2.56  | 43.202.126.218 | worker-2 (butterfly-worker, argocd, redis) |
| ip-10-0-2-199 | 10.0.2.199 | 미확인 | worker-3 (mysql) |
| ip-10-0-2-191 | 10.0.2.191 | 미확인 | worker-4 (clickhouse) |

> worker-3/4 EIP 확인 후 `prometheus/prometheus.yml` 의 `AWS_WORKER3_EIP`, `AWS_WORKER4_EIP` 치환 필요.

## 부팅 절차

### 1. 호스트 준비 (발급 EC2)

```bash
# Docker / Compose v2 설치 (Ubuntu 22.04)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER  # 재로그인 필요
```

### 2. 환경 변수 채우기

```bash
cd /home/ubuntu/monitoring
cp .env.example .env
vim .env  # GF_ADMIN_PASSWORD 설정
```

### 3. 부팅

```bash
docker compose up -d
docker compose ps   # prometheus / loki / grafana / node-exporter Up 확인
```

### 4. 동작 검증

```bash
# Grafana 헬스체크
curl https://k14b101.p.ssafy.io/grafana/api/health
# {"database": "ok", ...}

# Prometheus 자기 메트릭
curl http://localhost:9090/-/healthy

# Loki 헬스체크
curl http://localhost:3100/ready
```

## 운영 절차

### Prometheus rule reload (재시작 없이)

```bash
curl -X POST http://localhost:9090/-/reload
```

### Loki 디스크 가득

```bash
docker exec loki du -sh /loki
# retention_period 축소 후
docker compose restart loki
```

### nginx 설정 변경

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 백업

```bash
docker compose down
docker run --rm -v monitoring_prometheus-data:/data -v $(pwd):/backup ubuntu \
  tar czf /backup/prometheus-data-$(date +%Y%m%d).tar.gz /data
docker run --rm -v monitoring_loki-data:/data -v $(pwd):/backup ubuntu \
  tar czf /backup/loki-data-$(date +%Y%m%d).tar.gz /data
docker compose up -d
```

## 장애 대응

| 증상 | 1차 진단 | 조치 |
|------|---------|------|
| Grafana 502 | `docker compose ps grafana` | restart, 안 되면 `logs grafana` |
| Loki push 실패 | `curl http://localhost:3100/ready` | loki restart |
| Prometheus scrape 실패 | `curl http://localhost:9090/targets` | Security Group inbound 확인 (NodePort 30090) |
| Loki 쿼리 느림 | `du -sh /loki/chunks` | 라벨 cardinality 폭발 의심 |
| nginx 502 | `sudo nginx -t` | 설정 오류 확인 후 reload |

## 후속 작업

- **Phase 2**: K8s Promtail DaemonSet → `/loki/` push 설정
- **Phase 3**: Grafana 대시보드 JSON (api 응답성능 / 분석 집계 SLO)
- **Phase 4**: Alertmanager 알람 규칙 (SLO 위반 기준)
- **Phase 5**: Tempo + 분산 트레이싱 (별도 ADR)

## 참고

- ADR-0018: `backend/docs/adr/0018-monitoring-agent-deferred-alloy.md`
- log-policy: `backend/docs/operations/log-policy.md`
- cardinality 금지 라벨 5종: userId / projectId / campaignId / requestId / traceId
