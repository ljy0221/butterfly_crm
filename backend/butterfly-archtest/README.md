# butterfly-archtest

ArchUnit 기반 모듈 경계 검증 모듈.

## 역할

모듈러 모놀리스 전환의 첫 번째 단계로, 모듈 간 import 규칙을 코드 레벨에서 강제한다. 기존 위반은 baseline에 등록되어 빌드를 깨지 않고, **신규 위반만 차단**하는 점진 도입 방식.

## 동작 방식

- `ModularBoundaryTest`의 각 ArchRule이 `FreezingArchRule.freeze(...)`로 래핑됨
- 첫 실행 시 위반이 `src/test/resources/archunit_store/`에 baseline으로 자동 기록됨
- 이후 실행에서는 baseline에 없는 신규 위반만 실패시킴

## 실행

```bash
./gradlew :butterfly-archtest:test
```

전체 빌드에 자동 포함:

```bash
./gradlew clean build
```

## 새 ArchRule 추가

1. `ModularBoundaryTest`에 `@ArchTest static final ArchRule ...`로 규칙 작성
2. `FreezingArchRule.freeze(...)`로 감싸기
3. 매칭 클래스가 0개일 가능성이 있으면 `.allowEmptyShould(true)` 추가
4. `./gradlew :butterfly-archtest:test` 실행 → baseline 자동 등록

## baseline 갱신 (위반 1건 fix 후)

기본적으로 baseline은 위반이 줄어들면 자동으로 store에서 제거된다 (`allowStoreUpdate=true`). 별도 명령 불필요.

전체 baseline을 재생성하고 싶을 때:

```bash
ARCHUNIT_REFREEZE=true ./gradlew :butterfly-archtest:test
```

(또는 `archunit.freeze.refreeze=true` 시스템 프로퍼티 직접 전달)

## baseline 파일 구조

- `stored.rules` — rule 이름 → UUID 파일 매핑 (한국어 rule 이름은 유니코드 이스케이프됨)
- `<UUID>` — 각 rule별 위반 목록 (text)

## 모듈 개명 시 주의

P3(`customer→event`, `delivery→notification`, `analytics→reporting`) 머지 후에는 `ModularBoundaryTest`의 패키지 매처(`..delivery..`, `..analytics..` 등)를 새 이름으로 갱신하고, baseline을 재생성해야 한다:

```bash
ARCHUNIT_REFREEZE=true ./gradlew :butterfly-archtest:test
```

## 관련 문서

- 전체 계획: Confluence "모듈러 모놀리스 전환 합의" (PART 2 §2.9)
- Epic: S14P31B101-130
- Story: S14P31B101-131
