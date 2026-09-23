# platform/favorite/ — 찜 (플랫폼 팀 관리)

이 파일은 `server/src/main/java/setty/platform/favorite/` 내부를 다룰 때 읽는다.
공통 규칙은 `server/AGENTS.md`가 원본이다. 매물 컨벤션(`application`/`presentation`)을 따른다.

## 소유권

- 플랫폼 팀이 관리한다. `Favorite`는 `memberId`·`listingId`를 **Long id로만** 저장한다 (JPA 연관관계 없음).
- 매물 조회는 `ListingService.findSummaries()`·`ListingRepository`를 **읽기 전용**으로만 쓴다 (order의 DEC-05 선례). listing을 여기서 수정하지 않는다.

## 패키지 구조

- `domain`: `Favorite` 엔티티 (null 검증 생성자, getter만, setter 금지)
- `application`: `FavoriteService`
- `presentation`: `MyFavoriteController`(`/api/me/favorites`), `FavoriteStatusResponse`(record)
- 찜 전용 뷰 클래스(FavoriteView 등)를 만들지 않는다. `findMine`은 `ListingView.Summary`를 그대로 반환한다.

## 규칙

- 모든 엔드포인트는 `@LoginMember`의 `member.getId()`로만 자기 찜을 다룬다. 남의 데이터를 노출하지 않는다.
- PUT/DELETE는 멱등이다. 이미 찜해도 PUT은 204, 찜이 없어도 DELETE는 204. 상태 코드를 분기하지 않는다.
- 본인 매물은 찜할 수 없다. `add()`에서 `listing.isOwnedBy(memberId)`면 `CANNOT_FAVORITE_OWN_LISTING`으로 거절한다 (주문의 본인 매물 차단과 일관).
- 중복 방어는 `uk_favorites_member_listing` UNIQUE + `saveAndFlush` + `catch (DataIntegrityViolationException)` 무시로 한다. `remove`는 derived delete라 `@Transactional`이 필수다.
- **`FavoriteService.add()`에 `@Transactional`을 붙이지 않는다.** 트랜잭션으로 감싸면 UNIQUE 위반을 catch해도 Hibernate가 트랜잭션을 rollback-only로 마킹해 커밋 시 `UnexpectedRollbackException`(500)이 된다 — 테스트로 확인함. 저장을 리포지토리 자체 트랜잭션에 맡겨야 중복 요청이 조용히 성공한다. "일관성"을 이유로 되돌리지 말 것.

## 검증

- 도메인 null 검증은 Spring 없는 단위 테스트. add·remove·순서·삭제 제외·동시성은 실제 MySQL(Testcontainers)로 검증한다.
- 스키마 변경은 `schema.sql`에 멱등 SQL 추가로만 한다 (`ddl-auto: validate`).
