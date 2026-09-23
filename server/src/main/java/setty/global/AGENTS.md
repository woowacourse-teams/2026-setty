# global/ — 전역 인프라 (플랫폼 팀 관리)

이 파일은 `server/src/main/java/setty/global/` 내부를 다룰 때 읽는다.
공통 규칙은 `server/AGENTS.md`, 예외 처리는 `server/docs/exception-handling.md`가 원본이다.

## 소유권

- 플랫폼 팀이 관리한다. 배송 팀 작업 중 global 변경이 필요하면 코드를 고치지 말고 사용자에게 알린다.
- 예외 하나만 허용: `exception/ErrorCode.java`의 **"배송 (배송 팀)" 구역에 에러 코드를 추가**하는 것은 배송 팀도 할 수 있다. 다른 구역은 수정·삭제하지 않는다.

## 내용물

- 예외 인프라 — `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`, `ErrorResponse`
- 인터셉터 (인증 등)

## 규칙

- 예외 클래스·오류 응답·전역 예외 처리를 추가·수정하기 전에 반드시 `server/docs/exception-handling.md`를 읽는다.
- 예외 클래스는 `BusinessException` 하나만 유지한다. 도메인별 예외 클래스를 새로 만들지 않는다.
- 에러 응답 형식 `{ code, message }`를 바꾸지 않는다. 클라이언트와의 약속이다.
