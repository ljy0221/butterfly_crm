# Demo Inject Scripts (5/22 시연용)

시연 5.6 단계에서 Android 앱 이벤트 주입이 실패할 경우 즉시 사용하는 백업 스크립트입니다.

---

## 파일 구성

| 파일 | 설명 |
|------|------|
| `.env.example` | 환경변수 템플릿 |
| `inject-park-junho-view.sh` | 박준호 운동화 product_view 이벤트 주입 (권장) |
| `inject-fallback.sh` | .env 없이 즉시 실행 가능한 최소 백업 |

---

## 사전 준비

```bash
cp .env.example .env
# .env 파일에서 API_BASE / PROJECT_ID 확인
```

백엔드 기동 확인:

```bash
curl http://localhost:8080/actuator/health
```

---

## 사용 (시연 도중 — 두 번째 터미널에서)

```bash
bash inject-park-junho-view.sh
```

성공 메시지 확인 후 20~30초 기다렸다가
브라우저 세그먼트 빌더에서 **미리보기 실행** 클릭.

앱 / .env 모두 실패 시 최소 백업:

```bash
bash inject-fallback.sh
```

---

## 문제 해결

| 증상 | 확인 사항 |
|------|-----------|
| HTTP 4xx | `.env` 의 `PROJECT_ID` / `API_BASE` 값 확인 |
| HTTP 5xx | `docker logs butterfly-api` 백엔드 로그 확인 |
| 세그먼트 카운트 미반영 | Kafka consumer lag 확인 (`docker logs butterfly-worker`) |
| `uuidgen` 없음 | python3 폴백 자동 사용 (inject-park-junho-view.sh 내장) |

---

## 인증 방식

현재 `EventCollectController` 는 `X-Project-Id` 헤더만으로 인증합니다.
JWT / API-Key 인증이 5/22 이전에 추가된다면 이 파일의 curl 명령에
`-H "Authorization: Bearer <token>"` 헤더를 추가해야 합니다.
