# docs/optimizations/

butterfly 프로젝트에서 일어난 최적화·리팩터링·아키텍처 결정의 **Before/After 증거** 와 **의사결정 맥락** 을 표준 포맷으로 박제하는 디렉토리.

## 목적

- 5/22 발표 슬라이드 / 이력서 한 줄 / 기술 블로그 / 면접 STAR 답변에 그대로 인용 가능한 형태로 누적
- "기억에 의존한 자랑" 대신 **그 시점의 증거** (EXPLAIN, 측정값, git diff, 트레이드오프 메모) 보존
- 두 달 후 면접관 질문에 "그 때 데이터 어디 있냐"고 답할 수 있게

## 디렉토리 구조

```
docs/optimizations/
├── README.md                              ← 본 파일
├── INDEX.md                               ← 시간순 + 4축 매핑 인덱스
├── YYYY-MM-DD_<short-slug>.md             ← 표준 카드
└── _evidence/                             ← raw 증거 (로그, EXPLAIN, k6 결과, ci-yml 박제)
    └── YYYY-MM-DD_<topic>.txt
```

## 작성 원칙

1. **측정 없는 Before/After 금지** — "체감상 빨라졌다"만으로는 카드 생성 불가. 최소 1개 이상 정량 지표 또는 재현 가능한 증거 필요.
2. **Before와 After는 같은 환경** — 다른 데이터 규모/도구/시점 비교는 무의미. 환경 차이가 있으면 명시.
3. **트레이드오프 누락 금지** — 좋아진 것만 적고 나빠진 것을 숨기면 진정성 잃음. 메모리/복잡도/운영 부담 증가도 기록.
4. **추정값과 실측값 분리** — "예상 p95 350ms / 실측 p95 380ms" — 추정 정확도도 학습 자료.
5. **민감정보 마스킹** — 토큰/비밀번호/실제 사용자 데이터 캡처 금지.

## 카드 상태 모델

- `BEFORE-ONLY`: push/배포 직전 baseline 박제 완료, After 측정 대기
- `COMPLETE`: Before/After 모두 채워짐
- `RETROSPECTIVE`: 사후 회고로 작성 (정밀도 명시 필요)

## 카드 frontmatter 표준

```yaml
---
date: YYYY-MM-DD
slug: short-slug
category: PERF | SCHEMA | ARCH | SECURITY | DX | DECISION
tags: [#tag1, #tag2]
related-jira: <key>
related-adr: <id>
git-before: <commit-hash>
git-after: <commit-hash>
agent-source: <agent-name>
status: BEFORE-ONLY | COMPLETE | RETROSPECTIVE
---
```

## 카드 본문 표준 섹션

1. **한 줄 요약**
2. **Situation** (배경)
3. **Task** (목표)
4. **Action** (조치)
   - Before 증거 (line-numbered, 측정값, 박제 위치)
   - 적용된 변경 (커밋 SHA, diff stat)
5. **Result** (성과)
   - 정량 비교 표
   - 트레이드오프
6. **Reflection** (회고)
7. **References** (증거/코드/Jira/ADR)
8. **포트폴리오 변환** (이력서 / 블로그 / STAR / 4축 매핑)

## 다른 agent 산출물 흡수

- `performance-profiler` 진단 → PERF 카드 후보
- `migration-surgeon` 통합 검증 → SCHEMA 카드 후보
- `boundary-sentinel` SPI 리팩터링 → ARCH 카드 후보
- `aggregation-auditor` 정합성 → 정량 증거
- `security-audit-sentry` 권한 패치 → SECURITY 카드 후보
- `devops-engineer` CI 진단 → DX 카드 후보 (예: 본 디렉토리 첫 카드)

흡수 시 사용자 검토 필수 — 어떤 항목을 포트폴리오에 넣을지는 사용자가 결정.

## 5/22 발표 4축

- **멀티테넌트** — 테넌트 격리, 권한, 라우팅
- **비동기** — Kafka, DLQ/재시도, 멱등성
- **채널 추상화** — Email/SMS/Push/Kakao/...
- **성과 측정** — KPI/대시보드/통합 검증/CI DoD

각 카드 작성 시 어느 축에 매핑되는지 명시 → INDEX.md의 4축 섹션이 발표 슬라이드 골격.
