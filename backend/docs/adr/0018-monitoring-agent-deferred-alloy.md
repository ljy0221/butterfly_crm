---
status: accepted
date: 2026-05-11
decision-makers: 역할 6 (analytics/observability) 이재영
consulted: infra-researcher (sub-agent), DevOps Engineer (sub-agent)
informed: 전 역할 — K8s 배포 plan에 영향
supersedes: 메모리 결정 "관측 스택 결정 (2026-05-07) — Grafana Alloy 통합 채택"
---

# ADR-0018: 모니터링 agent로 node-exporter + Promtail 유지, Grafana Alloy 미도입

## Context and Problem Statement

butterfly 14일 K8s 배포 환경(발급 EC2 frontdoor + AWS K3s 4-node)에서 운영 관측 스택을 어떤 agent 조합으로 구성할 것인가를 결정해야 한다.

선행 결정(2026-05-07, 메모리 `project_observability_stack.md`)은 Grafana Alloy 단일 수집기로 logs+metrics+traces 셋을 통합하는 안이었다. 그러나 2026-05-11 K8s 배포 plan 교차 검토 과정에서 다음 사실이 드러났다:

- Alloy 전면 도입 시 **River 문법 학습 + 마이그레이션 + 메트릭 누락 디버깅 risk** 가 합산 1.5~2일 추가 산출 시간 소요
- 14일 산출 일정에서 가장 비싼 자원은 **시간**이며, Alloy의 narrative 가치(최신 통합 표준 채택)가 안정성 risk를 상쇄하지 못함
- Promtail이 2026-02-28 EOL 선언되었으나 **현재 LTS 단계**라 14일 산출 시점까지는 안정 동작 보장
- 한국어 자료/팀 친숙도가 node-exporter + Promtail 조합이 압도적

## Decision Drivers

- **14일 산출 안정성**: 미검증 도구의 디버깅 risk보다 검증된 조합의 즉시 동작이 더 가치 있음
- **시연 신뢰성**: 5/22 발표에서 메트릭/로그가 100% 안정적으로 보여야 함 (Alloy의 metric drop 사례 grafana/alloy#1224 등 보고됨)
- **narrative 가치**: "최신 도구 도입"보다 "EOL/마이그레이션을 인지하고 trade-off로 deferred"가 ADR-0012(Kafka 단일 broker), ADR-0014(t3a 결정)와 동일한 결정 패턴으로 일관성 있음
- **운영 부담 (1인)**: 두 agent 조합이지만 양쪽 모두 표준 Kubernetes manifest로 배포되어 운영 복잡도 차이 미미

## Considered Options

| 옵션 | 14일 산출량 | 안정성 | Narrative |
|---|---|---|---|
| **A. node-exporter + Promtail (DaemonSet) + Prometheus pull** | 0일 (즉시) | ✅ 검증된 표준 조합 | trade-off 명시 시 강함 |
| B. Grafana Alloy 전면 도입 | 1.5~2일 추가 | ⚠️ River 학습 + metric drop 사례 보고됨 (grafana/alloy#1224) | "최신 통합 채택" 강함 |
| C. 하이브리드 (Promtail만 Alloy로 교체) | 1일 추가 | ⚠️ agent 2종 동시 운영 | 약함 (이중가) |

## Decision Outcome

**옵션 A 채택** — node-exporter + Promtail 유지, Alloy 미도입.

### 구체 구성

- **AWS K3s 4-node 모두**: `node-exporter` DaemonSet + `promtail` DaemonSet (master는 toleration으로 배치)
- **발급 EC2 (`k14b101.p.ssafy.io`)**: docker-compose로 Prometheus + Grafana + Loki + Alertmanager
- **데이터 흐름**:
  - Prometheus가 AWS 노드 NodePort 30090 (node-exporter) + Pod `/actuator/prometheus` scrape (pull 모델, HTTPS)
  - Promtail이 K3s containerd 로그 위치 `/var/log/pods` tail → Loki HTTPS push (발급 EC2 :3100)
- **AWS sg-worker**: 30090 inbound 발급 EC2 IP/32만 허용
- **발급 EC2 UFW**: 9090 (Prometheus UI, 본인 IP), 3100 (Loki, AWS 노드 EIP/32 inbound 추가), 3000 (Grafana, 본인 IP)
- **logback**: 기존 `LogstashEncoder` JSON stdout 그대로 (`backend/butterfly-{api,worker}/src/main/resources/logback-spring.xml`)
- **Spring Boot Actuator**: `application.yml`에 `management.endpoints.web.exposure.include=health,prometheus` 추가 (현재 management block 부재 — 별도 작업)

### 라벨 cardinality 금지 (이전 결정에서 그대로 유지)

다음 값을 Loki 라벨 또는 Prometheus 메트릭 라벨에 박는 것은 BLOCKER (인덱스 폭발):
- `userId`, `projectId`, `campaignId`, `requestId`, `traceId`
→ 로그 본문 JSON field / 메트릭 exemplar / 트레이스 attribute로 보냄

### Tempo / 분산 트레이싱

14일 산출에서 **제외**. logback의 `traceId` MDC 키는 채워지지 않은 채로 남는다 (모순 인지). Production 전환 시 micrometer-tracing-bridge-otel 도입은 별도 ADR로 분리.

## Consequences

### Positive

- **즉시 동작**: 검증된 조합으로 5/22 발표까지 안정 시연 가능
- **운영 단순**: 한국어 자료 풍부, 팀 친숙도 높음, 트러블슈팅 비용 낮음
- **narrative 일관성**: ADR-0012(Kafka 단일), ADR-0014(t3a) 같은 "trade-off 인지 + deferred" 패턴과 동일한 결정 양식

### Negative

- **Promtail EOL 부담**: 2026-02-28 EOL이지만 14일 산출 시점에는 LTS로 무영향. Production 전환 시 Alloy 마이그레이션 ADR 필요
- **agent 2종 운영**: Alloy 단일 수집기 대비 컨테이너 2개. 단 둘 다 표준 manifest라 운영 복잡도 차이 미미
- **traces 미수집**: `traceId` MDC가 채워지지 않은 logback 설정과 모순 — 발표 시 "관측 3축 중 2축만 운영"임을 명시

### Risks

- **Promtail EOL 후 보안 패치 부재**: Production 전환 시점까지 Alloy 마이그레이션을 미루지 못함 — 별도 ADR 후보로 추적
- **K8s plan 6번(GitOps)와의 정합성**: ArgoCD가 monitoring namespace를 관리해야 함. plan 갱신 작업(S14P31B101-223)에서 명시 필요

## Verification Checklist

- [ ] 메모리 `project_observability_stack.md`와 본 ADR 일관성 확인 (메모리 이미 갱신됨)
- [ ] `backend/docs/adr/README.md` 색인에 ADR-0018 추가
- [ ] K8s plan 본문 갱신 (S14P31B101-223)에서 본 ADR 링크 활성화
- [ ] 후속 작업: `application.yml`에 `management.endpoints.web.exposure.include=health,prometheus` 추가 (별도 PR)
- [ ] 후속 ADR: Production 전환 시 "Alloy 통합 마이그레이션" ADR 신설

## References

- 폐기된 결정: 메모리 `project_observability_stack.md` 2026-05-07 항목 (이미 2026-05-11자로 재작성됨)
- 사용자 작성 K8s 14일 배포 plan (외부 문서)
- 관련 ADR: [ADR-0008](./0008-audit-log-architecture-v2.md) audit 로그 (운영 로그와 분리), [ADR-0014](./0014-events-persisted-topic.md) 모듈 경계
- 관련 코드: `backend/butterfly-{api,worker}/src/main/resources/logback-spring.xml`, `backend/butterfly-common/src/main/java/com/b101/butterfly/global/observability/`
- 관련 Jira: S14P31B101-221 (본 ADR 작성), S14P31B101-222 (RabbitMQ 제거), S14P31B101-223 (K8s plan 갱신)
- Promtail EOL 공지: <https://grafana.com/blog/2025/01/13/promtail-deprecation/>
- Alloy metric drop 사례 보고: <https://github.com/grafana/alloy/issues/1224>
