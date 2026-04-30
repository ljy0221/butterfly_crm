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

## baseline 갱신

`allowStoreUpdate` 기본값이 `false`이므로, 위반이 줄어들거나 늘어나도 baseline은 자동 갱신되지 않는다 (의도적 안전장치 — 신규 위반이 자동 흡수되어 ArchUnit 의미가 약해지는 사고를 막기 위함).

baseline을 갱신할 때는 의도를 명시적으로 표현한다.

### 1) 위반이 줄어든 경우 — 줄어든 위반을 baseline에서 제거

```bash
ARCHUNIT_ALLOW_STORE_UPDATE=true ./gradlew :butterfly-archtest:test
```

이러면 baseline에 등록된 위반 중 더 이상 코드에 존재하지 않는 항목이 baseline에서 사라진다. PR diff에 `archunit_store/<UUID>` 라인 감소로 진척이 보이게 된다.

### 2) 전체 baseline 재생성

매처 자체가 바뀌어 기존 baseline이 의미를 잃었거나, 처음부터 다시 잡고 싶을 때:

```bash
ARCHUNIT_REFREEZE=true ./gradlew :butterfly-archtest:test
```

### 주의

신규 위반은 어떤 옵션으로도 자동 흡수되지 않게 두는 것이 좋다 — `ARCHUNIT_ALLOW_STORE_UPDATE=true`는 "줄어든 위반 정리"용이지, "새 위반 흡수"용이 아니다. 새 위반이 정당하다고 판단되어 일시 baseline에 넣어야 한다면 `ARCHUNIT_REFREEZE=true`를 PR 코멘트에 명시하고 사유와 함께 commit한다.

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
