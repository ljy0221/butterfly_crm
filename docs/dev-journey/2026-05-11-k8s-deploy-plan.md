# Butterfly 14일 K8s 배포 인프라 Plan (v2 — 2026-05-11 갱신)

> **본 문서의 위치**: 이 plan은 원래 외부 메모(`idempotent-bubbling-wand.md`)에 작성되어 있었고,
> 2026-05-11 K8s 배포 plan 교차 검토(메인 + DevOps Engineer + Security & Audit Sentry agent)에서
> Critical 5건이 식별되어 본 갱신본을 dev-journey에 보존한다. 검토 원본은 별도 메모로 유지.
>
> **갱신 사항 (v1 → v2)**:
> - **RabbitMQ 제거 결정 반영** (Kafka 단일화) — S14P31B101-222 완료
> - **Observability A안 확정** (node-exporter + Promtail 유지, Alloy 미도입) — ADR-0018 / S14P31B101-221 완료
> - **ADR 후보 7건으로 확장** (기존 6 + ADR-0018 — Alloy deferred narrative)
> - **Step 0 (Decision & Critical Fixes) + Step 7.5 (Security Hardening) 추가** — 교차 검토 Critical 반영

---

## 1. Context

K8s 14일 배포 인프라 plan. 발급 EC2(`k14b101.p.ssafy.io`)가 SSAFY 통제권에 있으나 도메인 보유 자산 — 이를 frontdoor로 활용하고 AWS K3s 4-node에 control plane + workload + DB 배치.

핵심 제약과 결정 원칙은 v1과 동일. 변경 사항은 본 문서 상단 박스 참고.

## 2. 토폴로지 (변경 없음)

```
사용자 → [발급 EC2 = k14b101.p.ssafy.io]
           Nginx (TLS 종료, 정적 파일, /api reverse proxy)
           Prometheus + Grafana + Loki (관측 receiver)
           k6 부하 생성기 (Step 7 시연용)
           MySQL dump 백업 destination (cron rsync)
              ↓ HTTPS (public IP 화이트리스트)
        [AWS K3s 클러스터, 4노드, ap-northeast-2]
           master      t3a.medium  → control plane (taint)
           worker-1    t3a.large   → butterfly-api + Kafka (tier=heavy)
           worker-2    t3a.large   → butterfly-worker + Redis + ArgoCD (tier=light)
           mysql-node  t3a.medium  → MySQL StatefulSet (role=database)
```

비용 예측 v1과 동일 (~159k원/14일, 예산 200k 대비 44%).

## 3. 작업 단계 (10 step — Step 0 / 7.5 추가)

체크리스트로만. 외부 명령은 실행 단계에서 펼침.

- [ ] **Step 0: Decision & Critical Fixes (0.5d, 신규)** — 교차 검토 Critical 5건 선해소
  - ✅ Observability 단일화 결정 (A안 — Alloy 폐기, ADR-0018 작성 완료)
  - ✅ RabbitMQ 제거 결정 + 코드 제거 (S14P31B101-222 완료)
  - [ ] Jib `backend/build.gradle:106-114` arm64 platform 제거 (CI -3~5분)
  - [ ] HPA max=2 제한 + api/kafka resources.limits.memory 명시
  - [ ] CI manifest push에 `-o ci.skip` 명시 패턴 확정
- [ ] **Step 1: AWS 프로비저닝** — IAM + billing alarm + VPC(`10.0.0.0/16`) + public subnet 2개 + IGW + SG 3종 + EC2 4대 + EIP 4개. 통과: 4대 SSH 가능
- [ ] **Step 2: K3s 클러스터** — master에 server 설치(`--disable traefik --disable servicelb`), 3개 worker join, taint/label 적용, kubeconfig 로컬 복사. 통과: `kubectl get nodes` 4 Ready
- [ ] **Step 3: 발급 EC2 frontdoor** — DNS 검증 먼저(`dig k14b101.p.ssafy.io` → 발급 EC2 public IP 일치 + `curl ifconfig.me`로 IP 확인) → UFW + Nginx + 정적 SPA 디렉터리 + certbot Let's Encrypt(`--staging`으로 먼저 검증, rate limit 주 5회 주의) + Docker Compose로 Prometheus/Grafana/**Loki**. 통과: `curl -I https://k14b101.p.ssafy.io` 200
- [ ] **Step 4: Kustomize manifest** — `/deploy/k8s/`에 base + overlays/dev 작성. api/worker Deployment, kafka StatefulSet(worker-1 핀), mysql StatefulSet(mysql-node 핀), redis Deployment. **monitoring namespace에 node-exporter + Promtail DaemonSet 추가** (4-node 모두). PVC는 K3s 기본 `local-path` 사용 (EBS CSI 미도입 — 노드 루트 디스크 위에 동작). **Secret 관리**: `secrets.yaml`을 `.gitignore`에 추가, ArgoCD가 Git에서 가져가지 않도록 분리 → 본인이 1회 `kubectl apply -f secrets.yaml`로 직접 생성(DB 비번/JWT secret). Flyway는 앱 부팅 시 자동. 통과: `kubectl apply --dry-run=client -k ...`
- [ ] **Step 5: CI에 manifest 자동 갱신** — GitLab Project Access Token 발급(scope: `write_repository`) → CI/CD Variables에 `GIT_PUSH_TOKEN` 등록(masked) → `.gitlab-ci.yml`에 `update-manifest` stage 추가 (kustomize edit set image → `git push https://oauth2:$GIT_PUSH_TOKEN@gitlab.../...git -o ci.skip`). 통과: develop push 시 manifest에 새 SHA 커밋
- [ ] **Step 6: ArgoCD 설치 + Application** — worker-2 `tier=light` 슬롯에 배치, GitLab Deploy Key(read-only) 발급 → ArgoCD `argocd repo add` 명령으로 등록(repo credentials), 단일 Application(자동 sync, prune, selfHeal), Nginx에 `/argo` reverse proxy. 통과: 모든 Pod Running, Flyway 적용 로그
- [ ] **Step 7: E2E 검증 + 부하 테스트 + HPA 시연** — frontdoor → API → MySQL → Kafka → worker 라이트라이트 (검증 후). 이어서 k6/hey로 부하 주면서 `HorizontalPodAutoscaler`로 butterfly-api Pod 자동 스케일링 시연, Grafana CPU/Pod 그래프 캡처
- [ ] **Step 7.5: Security Hardening (1d, 신규)** — 발표 전 필수
  - [ ] Caddy/Tailscale로 발급 EC2 → AWS worker 평문 HTTP 해소 (Critical C4)
  - [ ] SOPS + age로 secrets 암호화 (Critical C5)
  - [ ] ArgoCD admin 비번 변경 + Nginx `/argo` Basic Auth 추가 (High H7)
  - [ ] K3s `--audit-log-path` + `--audit-policy-file` + Promtail 수집 (High H6)
  - [ ] JWT secret 90일 rotation 절차 문서 (High H8)
  - [ ] 발급 EC2 IP 변경 자동 동기화 cron (High H4)
  - [ ] PVC 복구 runbook + 5/14 1회 dry-run (High H5)
- [ ] **Step 8: 시연 준비** — 모니터링 대시보드 + 알람 + 시연 시나리오 + 장애 복구 데모 + 발표자료 + ADR 정리

## 4. 서버별 워크로드 배치 (변경: monitoring agent 명시)

| 서버 | 종류 | Taint/Label | 워크로드 | 메모리 사용 |
|------|------|-------------|----------|-------------|
| 발급 EC2 (4vCPU/16GB) | K8s 외부 frontdoor + 이슈 보조 (host + Docker) | (K8s 클러스터 외) | Nginx (host), 정적 SPA, **Prometheus + Grafana + Loki** + **k6 부하 생성기** + **MySQL dump 백업 destination** (Docker) | ~6~7GB |
| master (t3a.medium, 4GB) | K3s server | Taint: `CriticalAddonsOnly=true:NoSchedule` (단 monitoring DaemonSet은 toleration으로 배치) | K3s server, kine(SQLite), **node-exporter + Promtail** | ~1.5GB |
| worker-1 (t3a.large, 8GB) | K3s agent | Label: `tier=heavy` | butterfly-api(1, HPA max=2), Kafka StatefulSet(1, KRaft 단일 브로커), **node-exporter + Promtail** | ~5GB |
| worker-2 (t3a.large, 8GB) | K3s agent | Label: `tier=light` | butterfly-worker(1), Redis(1), ArgoCD 4 pods, **node-exporter + Promtail** | ~4GB |
| mysql-node (t3a.medium, 4GB) | K3s agent | Label: `role=database` | MySQL StatefulSet(1), **node-exporter + Promtail** | ~2GB |

**StatefulSet PVC** (K3s 기본 `local-path` provisioner 사용 — EBS CSI 미도입):
- kafka-data: 5Gi (worker-1 노드 루트 디스크 위 디렉터리)
- mysql-data: 20Gi (mysql-node 노드 루트 디스크 위 디렉터리, mysql-node EC2 루트 볼륨은 60GB로 잡아 백업 사전 확보)

**HPA**: butterfly-api에 적용 (CPU 70% 임계, **replicas 1~2** — 단일 노드 OOM 회피, 교차 검토 반영). Step 7 시연용.

## 5. 네트워크/보안 핵심 (변경 없음 — Step 7.5에서 강화)

v1 본문 동일. 단 **Step 7.5에서 다음을 강화**:
- 발급 EC2 → worker 평문 HTTP → Caddy/Tailscale 적용 후 mTLS 또는 mesh 암호화
- SG IP whitelist만 의존 → 자동 동기화 + 알람
- `secrets.yaml` 평문 → SOPS + age 암호화 후 git에 commit (GitOps 원칙 복원)
- K3s `--secrets-encryption` 활성화 (etcd at-rest 암호화)

## 6. ArgoCD GitOps 설계 (변경 없음)

v1 본문 동일. 단 Step 7.5에서:
- ArgoCD admin 비번 즉시 변경 + secret 삭제
- Nginx `/argo` location에 Basic Auth (또는 GitHub OIDC)
- selfHeal+prune의 위험 완화: secrets에 `argocd.argoproj.io/sync-options: Prune=false` annotation

## 7. 추천 서브에이전트 3개 (변경 없음)

v1 본문 동일.

## 8. 검증 (End-to-End) — 변경: Kafka 단일 메시징 명시

| 체크 | 명령 | 기대 |
|------|------|------|
| Frontdoor TLS | `curl -I https://k14b101.p.ssafy.io` | 200 + LE cert |
| API 라우팅 | `curl https://k14b101.p.ssafy.io/api/v1/actuator/health` | `{"status":"UP"}` |
| MySQL 연결 | api Pod 로그에 Flyway "Successfully applied" | V1~V3 적용 |
| GitOps 자동화 | develop 푸시 후 5분 내 `kubectl get app -n argocd` | `Synced/Healthy` + 새 SHA |
| 모니터링 (변경) | Grafana `up{job="kubernetes-nodes"}` + Loki 쿼리 `{namespace="butterfly"} |= "INFO"` | 4 노드 + 로그 조회 가능 |
| Worker 통합 (변경) | **Kafka로 test event 발행 → MySQL row 확인 (RabbitMQ 검증 제거)** | row 존재 |
| HPA 시연 | `k6 run loadtest.js` 동안 `kubectl get hpa -w` | replicas 1 → 2 (max=2), CPU > 70% |

## 9. 서버별 Architecture Stack — Observability 명시 갱신

### 발급 EC2 (`k14b101.p.ssafy.io`) — Frontdoor + Observability Receiver

```
Ubuntu 22.04 + UFW (80/443 ALL, 22/9090/3000 본인 IP, 3100 AWS 노드 EIP/32만)
├─ [systemd] Nginx
│    443: TLS 종료 (Let's Encrypt)
│    /          → /var/www/butterfly (정적 SPA)
│    /api/*     → AWS worker-1 EIP:30080 (HTTP, SG에 발급 EC2 IP/32만 허용)
│    /argo/*    → AWS worker-2 EIP:30888 (Step 7.5에서 Basic Auth 추가)
│    /grafana/* → 127.0.0.1:3000
└─ [Docker, docker-compose.yml]
     prometheus (9090, AWS 노드 NodePort 30090 scrape)
     grafana    (3000, Prometheus + Loki datasource)
     loki       (3100, AWS 각 노드 promtail로부터 log push 수신)
     k6         (Step 7 시연 시 ad-hoc 실행, --vus 50 --duration 5m)
└─ [host cron] mysql-backup.sh (매일 02:00, AWS mysql-node에서 mysqldump → SSAFY로 rsync → /var/backups/mysql/)
```

### AWS master (t3a.medium) — Control plane + monitoring agent

```
Ubuntu 22.04
├─ [systemd] k3s server
│    └─ kube-apiserver (6443) + kube-controller-manager + kube-scheduler + kine→SQLite
│  Taint: CriticalAddonsOnly=true:NoSchedule
└─ [K8s namespace: monitoring]
     DaemonSet node-exporter (toleration으로 master에도 배치, NodePort 30090)
     DaemonSet promtail (toleration, /var/log/pods tail → 발급 EC2 Loki:3100 push)
```

### AWS worker-1 (t3a.large, tier=heavy)

```
Ubuntu 22.04
├─ [systemd] k3s agent (kubelet, kube-proxy, flannel) + containerd
   [K8s namespace: butterfly]
     Deployment butterfly-api    (replicas=1, HPA 1~2, resources.limits.memory=2Gi)
     StatefulSet kafka            (replicas=1, KRaft combined mode, KAFKA_HEAP_OPTS=-Xmx1G)
       PVC kafka-data 5Gi (local-path)
   [namespace: monitoring]
     DaemonSet node-exporter (NodePort 30090)
     DaemonSet promtail
```

### AWS worker-2 (t3a.large, tier=light)

```
Ubuntu 22.04
└─ [systemd] k3s agent + containerd
   [K8s namespace: butterfly]
     Deployment butterfly-worker (replicas=1)
     Deployment redis            (replicas=1)
   [namespace: argocd]
     argocd-server (NodePort 30888) + argocd-repo-server + argocd-application-controller + argocd-redis
   [namespace: monitoring]
     DaemonSet node-exporter
     DaemonSet promtail
```

### AWS mysql-node (t3a.medium, role=database)

```
Ubuntu 22.04
└─ [systemd] k3s agent + containerd
   [K8s namespace: butterfly]
     StatefulSet mysql            (replicas=1, innodb_buffer_pool_size=1G, max_connections=50)
       nodeSelector: role=database
       PVC mysql-data 20Gi (local-path, mysql-node 루트 60GB)
   [namespace: monitoring]
     DaemonSet node-exporter
     DaemonSet promtail
```

## 10. 데이터 흐름 — Promtail 추가

```
[브라우저] https://k14b101.p.ssafy.io/api/v1/events
    → HTTPS
[발급 EC2 Nginx] TLS 종료, /api/* 매칭
    → HTTPS (public, SG 화이트리스트)
[AWS worker-1 NodePort 30080 HTTP]
    → kube-proxy
[Service: api] → [Pod: butterfly-api on worker-1]
    ├─ redis.butterfly:6379       → [worker-2 redis Pod]
    ├─ kafka.butterfly:9092       → [worker-1 kafka Pod]
    └─ mysql.butterfly:3306       → [mysql-node mysql Pod]
    → 응답 역경로

[비동기 — Kafka 단일화 (RabbitMQ 제거)]
[butterfly-api] → produce → [kafka] → consume → [butterfly-worker on worker-2]
                                              → [mysql] / [redis]

[관측 — A안 (node-exporter + Promtail)]
[Prometheus on 발급 EC2] ←─ scrape ─── [4 AWS 노드 :30090 node-exporter]
[Loki on 발급 EC2 :3100] ←── push ──── [4 AWS 노드 promtail (/var/log/pods tail)]
[Grafana on 발급 EC2] → Prometheus + Loki datasource → 대시보드
```

## 11. 위험 시나리오 Top 3 (변경 없음)

v1 본문 동일.

## 12. 필요 ADR 후보 (6 → 7건으로 확장)

`backend/docs/adr/`에 작성. 결정 7개:

1. K3s vs EKS vs kubeadm 채택 사유
2. 발급 EC2를 K8s 클러스터에서 분리하고 외부 frontdoor + 이슈 보조로 사용한 사유
3. 모든 stateful(MySQL, Kafka, Redis)을 K8s StatefulSet으로 통일 + MySQL 전용 노드 분리한 사유
4. Kafka 단일 브로커 KRaft combined mode 채택 사유
5. K3s 기본 local-path provisioner 사용, EBS CSI Driver 미도입 사유
6. ArgoCD polling interval 30s + webhook 미도입 사유
7. **(신규) ADR-0018 — 모니터링 agent로 node-exporter+Promtail 유지, Grafana Alloy 미도입** (✅ 작성 완료, 2026-05-11)

추가 ADR 후보 (Step 7.5 진행 후 작성):
8. K3s `--secrets-encryption` + SOPS 도입 사유 (etcd at-rest + GitOps 일관성)
9. 발급 EC2 → worker 구간 mesh/mTLS 도입 또는 Caddy 사이드카 사유
10. K3s audit log 활성화 사유 (ADR-0008 immutability narrative 인프라 확장)

## 13. 참조할 핵심 파일/디렉터리

- [.gitlab-ci.yml](../../.gitlab-ci.yml) — 수정: `update-manifest` stage 추가
- [backend/butterfly-api/build.gradle](../../backend/butterfly-api/build.gradle) — Jib `to.image` 태그 전략 확인
- [backend/butterfly-worker/build.gradle](../../backend/butterfly-worker/build.gradle) — 동일
- [backend/build.gradle](../../backend/build.gradle) — root, Jib 공통 설정 (arm64 제거 작업 대상)
- [backend/docker-compose.yml](../../backend/docker-compose.yml) — K8s manifest 작성 시 환경변수/포트 레퍼런스 (RabbitMQ 제거됨)
- [backend/CLAUDE.md](../../backend/CLAUDE.md) — 모듈 의존 방향, 응답 규약
- [backend/docs/adr/0018-monitoring-agent-deferred-alloy.md](../../backend/docs/adr/0018-monitoring-agent-deferred-alloy.md) — Observability A안 결정
- **신설**: [deploy/k8s/](../../deploy/k8s/) — Kustomize base + overlays/dev (이 plan의 핵심 산출물)
- **신설**: `loadtest/` — k6 스크립트 (Step 7 부하 테스트용)

## 14. 결정 사고과정 (생략 — v1 본문 그대로)

v1 14-1 ~ 14-6 결정 모두 유효. 추가 결정:

### 14-7. Observability agent (node-exporter+Promtail vs Alloy)

| 옵션 | 14일 산출량 | 안정성 | Narrative |
|------|-----------|--------|-----------|
| **A. node-exporter + Promtail** | 0일 (즉시) | ✅ 검증된 표준 | trade-off 명시 시 강함 |
| B. Grafana Alloy 전면 도입 | 1.5~2일 추가 | ⚠️ River 학습 + metric drop 사례 | "최신 통합 채택" 강함 |
| C. 하이브리드 (Promtail만 Alloy) | 1일 추가 | ⚠️ agent 2종 동시 운영 | 약함 |

**한 줄 사유**: 14일 산출의 가장 비싼 자원이 시간. Alloy narrative 가치보다 안정성 risk가 큼.
"Promtail EOL 인지하나 14일 산출 안정성 우선으로 deferred" → ADR-0018로 명시.

### 14-8. 메시징 (Kafka vs Kafka+RabbitMQ)

| 옵션 | 운영 부담 | 의도된 사용처 | 결정 |
|------|----------|--------------|------|
| **Kafka 단독** | 컨테이너 1개 | 모든 비동기 처리 | ✅ 채택 |
| Kafka + RabbitMQ | 컨테이너 2개 + StatefulSet 추가 | RabbitMQ는 발송 큐(원래 의도) | ❌ 사용처 0건 (`@RabbitListener`/`RabbitTemplate` grep 결과) |

**한 줄 사유**: starter 의존성은 잔존했으나 실제 사용처 0건. K8s 배치 plan 교차 검토에서 부팅 시 매번 "Connection refused" 경고 + 운영 부담만 추가됨이 발견되어 제거 결정. → ADR-0018과 같은 trade-off 패턴 (deferred narrative 없이 단순 정리).

---

이상 7개 (기존 6 + ADR-0018) ADR 후보. plan 승인 후 `backend/docs/adr/` 또는 `docs/decisions/`에 ADR 파일 작성. 작성 시 본인 언어로 다시 풀이 — "왜 이게 내 상황에 맞는지"를 본인 말로 추가 (CLAUDE.local.md 룰).
