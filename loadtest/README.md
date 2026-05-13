# loadtest

butterfly-api 부하 시연용 k6 스크립트.

## 실행

받은 EC2 docker-compose에서 ad-hoc 실행:

```bash
# 받은 EC2 SSH
cd ~/observability
docker compose --profile loadtest run --rm k6
```

stages 합계 ~7분. 자세한 절차/관찰 체크리스트는 `docs/plans/인프라-step7-부하시연-HPA-runbook.md` 참조 (개인 노트, gitignore).

## 환경 변수

- `BASE_URL` (default `https://k14b101.p.ssafy.io`)
- `API_PATH` (default `/api/v1/auth/login`)

## 의도적으로 실패하는 자격

bcrypt 비교가 풀로 도는 것이 목적. 실제 로그인 성공은 시연 narrative에 무관.
