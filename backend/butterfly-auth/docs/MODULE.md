# butterfly-auth

## 역할

회원 인증, 프로젝트 관리, 프로젝트별 역할 기반 인가를 담당하는 모듈.

## 주요 기능

- **회원 인증**: 회원가입, 로그인(JWT 발급), 로그아웃, 토큰 갱신, 내 정보 조회
- **프로젝트 관리**: 프로젝트 CRUD, API Key 발급/재발급
- **프로젝트 멤버 관리**: 멤버 초대, 역할 변경(OWNER/ADMIN/EDITOR/VIEWER), 멤버 제거
- **인가**: `@PreAuthorize`를 통한 프로젝트별 역할 기반 접근 제어 (ADR-0005)

## 담당 테이블

`members`, `projects`, `project_members`

## API 경로

- `/api/auth/**` — 회원 인증 (인증 불필요)
- `/api/projects/**` — 프로젝트/멤버 관리 (JWT 인증 필요)

## 의존 관계

- `butterfly-common`에 의존
- Spring Security, Spring Data JPA, MySQL
