# platform/member/ — 회원·인증 (플랫폼 팀 관리)

이 파일은 `server/src/main/java/setty/platform/member/` 내부를 다룰 때 읽는다.
공통 규칙은 `server/AGENTS.md`가 원본이다.

## 소유권

- 플랫폼 팀이 관리한다. 다른 팀 작업 중 변경이 필요하면 코드를 고치지 말고 사용자에게 알린다.

## 내용물

- `Member` — 회원 엔티티. loginId(유니크), password(BCrypt 해시), role, phoneNumber, address, token(로그인 토큰)
- `AuthController` — `POST /api/auth/signup` / `POST /api/auth/login` / `GET /api/auth/me`
- `AuthService` — 가입(BCrypt 해시 저장), 로그인(비밀번호 검증 + UUID 토큰 발급·회전)

인증 인프라(인터셉터·`@LoginMember` 리졸버)는 여기가 아니라 `setty/global/auth/`에 있다.

## 규칙

- **비밀번호는 BCrypt 해시만 저장한다.** 원문을 저장·로깅·응답에 노출하지 않는다.
- **토큰은 로그인마다 회전한다** (UUID 재발급 → 기존 토큰 무효). 1계정 1기기가 의도된 동작이므로 "이전 토큰 유지"로 바꾸지 않는다.
- 응답 DTO에 `password`·`token` 필드를 넣지 않는다 (`token`은 로그인 응답 한 곳만 예외).
- 가입 요청의 role은 String + `@Pattern` 검증이다. enum 직바인딩으로 바꾸면 오타 입력이 400 대신 500으로 떨어진다.
- 아이디 없음/비밀번호 불일치는 구분 없이 401 `LOGIN_FAILED` 하나로 응답한다 (계정 존재 여부를 노출하지 않기 위함).
- 스키마 변경은 `schema.sql`에 멱등 SQL 추가로만 한다 (`ddl-auto: validate`).

## 예정된 변경 (팀 논의 중)

- 기사(driver)를 members에서 분리해 배송 팀이 별도 관리하는 방향이 논의 중이다. 확정되면 role은 `MEMBER/ADMIN`으로 바뀌고 가입 요청에서 role 입력이 사라진다.
