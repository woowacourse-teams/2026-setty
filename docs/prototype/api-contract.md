# SETTY 프로토타입 API 계약

- 상태: **구현 완료(Issue #140), 실제 사용자 공개 전**
- 범위: 판매자가 매물을 올리고 구매자가 로그인 없이 쪽지를 보내는 프로토타입 흐름
- 위치: `server/src/main/java/setty/prototype/`

예상 견적·배차 요청과는 요청·상태·인증이 겹치지 않는 별도 흐름입니다. 같은 서버 애플리케이션에서 동작하지만 테이블(`prototype_*`)과 오류 형식, 인증 방식을 공유하지 않습니다.

## 결론

**총 10개 API.** 휴대폰 번호를 로그인 ID로 사용하고, 비밀번호는 사용자가 지정합니다. 회원가입 API를 따로 두지 않고 로그인 요청이 가입을 겸합니다. 매물 소유권은 로그인 세션으로 판별합니다.

당근 링크는 별도 필드로 관리하지 않습니다. 필요한 경우 `description`에 일반 텍스트로 입력합니다.

## 전체 API 표

| # | Method | Endpoint | 인증 | Request | 성공 Response |
| --- | --- | --- | --- | --- | --- |
| 1 | `POST` | `/api/auth/login` | 없음 | `LoginRequest` | `201`(가입) 또는 `200 AuthMemberResponse` |
| 2 | `POST` | `/api/auth/logout` | 필요 | 없음 | `204` |
| 3 | `GET` | `/api/me/seller-page` | 필요 | 없음 | `200 SellerPageResponse` |
| 4 | `GET` | `/api/listings` | 없음 | 없음 | `200 ListingListResponse` |
| 5 | `GET` | `/api/listings/{listingId}` | 없음 | Path | `200 ListingDetailResponse` |
| 6 | `POST` | `/api/listings` | 필요 | Multipart | `201 CreateListingResponse` |
| 7 | `PATCH` | `/api/listings/{listingId}` | 소유자 | JSON | `204` |
| 8 | `DELETE` | `/api/listings/{listingId}` | 소유자 | 없음 | `204` |
| 9 | `POST` | `/api/listings/{listingId}/messages` | 없음 | JSON | `201 CreateMessageResponse` |
| 10 | `GET` | `/api/listings/{listingId}/messages` | 소유자 | 없음 | `200 MessageListResponse` |

## 공통 세션 인증

로그인(첫 로그인의 가입 포함) 성공 시 세션 쿠키를 발급합니다.

```
Set-Cookie: JSESSIONID=opaque-session-id; HttpOnly; SameSite=Lax; Path=/
```

프론트 요청에는 쿠키를 포함합니다.

```
fetch(url, {
  credentials: "include"
});
```

HTTPS 배포 환경에서는 쿠키에 `Secure`를 추가합니다(`server.servlet.session.cookie.secure`).

세션에는 회원 식별자만 담고 휴대폰 번호와 비밀번호는 담지 않습니다. 로그인 시 이전 세션을 폐기하고 새 세션을 발급합니다.

---

## 1. 로그인

가입과 로그인을 한 요청으로 처리합니다. 처음 보는 휴대폰 번호면 요청에 담긴 비밀번호로 회원을 만들고 바로 로그인시킵니다. 별도의 회원가입 API는 없습니다.

```
POST /api/auth/login
Content-Type: application/json
```

### Request

```
{
  "phoneNumber": "010-0000-0000",
  "password": "example-password"
}
```

### Response `201` — 처음 보는 번호라 가입까지 한 경우

```
{
  "phoneNumber": "01000000000"
}
```

```
Set-Cookie: JSESSIONID=opaque-session-id; HttpOnly; SameSite=Lax; Path=/
```

### Response `200` — 이미 가입한 번호로 로그인한 경우

```
{
  "phoneNumber": "01000000000"
}
```

```
Set-Cookie: JSESSIONID=opaque-session-id; HttpOnly; SameSite=Lax; Path=/
```

프론트는 상태 코드로 첫 가입과 재로그인을 구분합니다. 응답 본문은 같습니다.

### 비밀번호 불일치 `401`

이미 가입한 번호에 다른 비밀번호를 보낸 경우입니다. 이때 회원을 새로 만들지 않으므로 처음 정한 비밀번호는 그대로 유지됩니다.

```
{
  "code": "INVALID_CREDENTIALS",
  "message": "휴대폰 번호 또는 비밀번호가 일치하지 않습니다."
}
```

가입 자체가 이 요청으로 이뤄지므로 비밀번호 규칙(8~100자)과 휴대폰 번호 형식을 로그인 요청에서 확인합니다. 어기면 `400 INVALID_REQUEST`입니다.

---

## 2. 로그아웃

```
POST /api/auth/logout
Cookie: JSESSIONID=opaque-session-id
```

### Response `204`

세션을 제거합니다. 응답 본문 없음.

---

## 3. 판매자 마이페이지

현재 로그인 회원이 등록한 모든 매물과 받은 쪽지 수를 반환합니다. 최신 등록순입니다.

```
GET /api/me/seller-page
Cookie: JSESSIONID=opaque-session-id
```

### Response `200`

```
{
  "seller": {
    "phoneNumber": "01000000000"
  },
  "summary": {
    "listingCount": 2,
    "messageCount": 4
  },
  "listings": [
    {
      "id": 1,
      "title": "원목 책상",
      "thumbnailUrl": "https://example.com/images/1.jpg",
      "pickupTimeText": "평일 오후 7시 이후",
      "canHelpMove": true,
      "messageCount": 3,
      "latestMessageAt": "2026-08-21T15:00:00+09:00",
      "createdAt": "2026-08-21T14:00:00+09:00"
    },
    {
      "id": 2,
      "title": "소형 냉장고",
      "thumbnailUrl": "https://example.com/images/2.jpg",
      "pickupTimeText": "주말 가능",
      "canHelpMove": false,
      "messageCount": 1,
      "latestMessageAt": "2026-08-21T14:30:00+09:00",
      "createdAt": "2026-08-21T13:00:00+09:00"
    }
  ]
}
```

쪽지가 없는 매물은 `messageCount`가 `0`, `latestMessageAt`이 `null`입니다.

등록한 매물이 없으면:

```
{
  "seller": {
    "phoneNumber": "01000000000"
  },
  "summary": {
    "listingCount": 0,
    "messageCount": 0
  },
  "listings": []
}
```

### 세션 없음 `401`

```
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "로그인이 필요합니다."
}
```

---

## 4. 매물 목록 조회

최신 등록순으로 반환합니다.

```
GET /api/listings
```

### Response `200`

```
{
  "items": [
    {
      "id": 1,
      "title": "원목 책상",
      "thumbnailUrl": "https://example.com/images/1.jpg",
      "pickupTimeText": "평일 오후 7시 이후",
      "canHelpMove": true,
      "createdAt": "2026-08-21T14:00:00+09:00"
    },
    {
      "id": 2,
      "title": "소형 냉장고",
      "thumbnailUrl": "https://example.com/images/2.jpg",
      "pickupTimeText": "주말 가능",
      "canHelpMove": false,
      "createdAt": "2026-08-21T13:00:00+09:00"
    }
  ]
}
```

매물이 없으면:

```
{
  "items": []
}
```

---

## 5. 매물 상세 조회

```
GET /api/listings/1
```

### Response `200`

```
{
  "id": 1,
  "title": "원목 책상",
  "description": "사용감이 조금 있습니다. 당근 링크: https://example.com/item",
  "pickupTimeText": "평일 오후 7시 이후",
  "canHelpMove": true,
  "images": [
    {
      "id": 11,
      "url": "https://example.com/images/1.jpg",
      "displayOrder": 1
    },
    {
      "id": 12,
      "url": "https://example.com/images/2.jpg",
      "displayOrder": 2
    }
  ],
  "createdAt": "2026-08-21T14:00:00+09:00",
  "updatedAt": "2026-08-21T14:00:00+09:00"
}
```

판매자 휴대폰 번호와 비밀번호는 공개하지 않습니다.

---

## 6. 매물 등록

현재 로그인 회원을 매물 소유자로 저장합니다.

```
POST /api/listings
Content-Type: multipart/form-data
Cookie: JSESSIONID=opaque-session-id
```

### Request Part: `request`

```
{
  "title": "원목 책상",
  "description": "사용감이 조금 있습니다. 당근 링크: https://example.com/item",
  "pickupTimeText": "평일 오후 7시 이후",
  "canHelpMove": true
}
```

### Request Part: `images`

```
images: image1.jpg
images: image2.jpg
```

- 최소 1장
- 최대 5장
- 전체 요청 최대 25MB

사진은 S3의 `setty/images/listings/` 아래에 저장하고 공개 URL을 돌려줍니다. 업로드 순서가 `displayOrder`가 되고, 첫 번째 사진이 목록·마이페이지의 `thumbnailUrl`입니다.

### Response `201`

```
{
  "listingId": 1,
  "createdAt": "2026-08-21T14:00:00+09:00"
}
```

```
Location: /api/listings/1
```

---

## 7. 매물 수정

현재 로그인 회원이 해당 매물의 소유자여야 합니다.

```
PATCH /api/listings/1
Content-Type: application/json
Cookie: JSESSIONID=opaque-session-id
```

### Request

변경할 필드만 전달합니다. 최소 한 개 필드가 필요합니다. 사진은 수정 대상이 아닙니다.

```
{
  "title": "원목 책상 급처",
  "description": "이번 주말까지만 판매합니다.",
  "pickupTimeText": "토요일 오전 가능",
  "canHelpMove": false
}
```

### Response `204`

응답 본문 없음.

### 다른 회원의 매물 `403`

```
{
  "code": "LISTING_ACCESS_DENIED",
  "message": "해당 매물을 수정할 권한이 없습니다."
}
```

---

## 8. 매물 삭제

현재 로그인 회원이 해당 매물의 소유자여야 합니다.

```
DELETE /api/listings/1
Cookie: JSESSIONID=opaque-session-id
```

### Response `204`

응답 본문 없음. 연결된 사진 정보와 쪽지도 함께 삭제합니다. S3에 올라간 사진 파일 삭제는 이 범위에 없습니다.

### 다른 회원의 매물 `403`

```
{
  "code": "LISTING_ACCESS_DENIED",
  "message": "해당 매물을 삭제할 권한이 없습니다."
}
```

---

## 9. 구매 쪽지 전송

로그인 없이 누구나 전송할 수 있습니다.

```
POST /api/listings/1/messages
Content-Type: application/json
```

### Request

```
{
  "content": "구매하고 싶습니다. 토요일 픽업 가능한가요?"
}
```

### Response `201`

```
{
  "messageId": 31,
  "createdAt": "2026-08-21T15:00:00+09:00"
}
```

구매자 개인정보는 수집하거나 판매자에게 노출하지 않습니다. 저장하는 값은 쪽지 내용과 시각뿐입니다.

---

## 10. 판매자 쪽지 조회

현재 로그인 회원이 해당 매물의 소유자여야 합니다. 최신 쪽지순으로 반환합니다.

```
GET /api/listings/1/messages
Cookie: JSESSIONID=opaque-session-id
```

### Response `200`

```
{
  "listingId": 1,
  "items": [
    {
      "id": 31,
      "content": "구매하고 싶습니다. 토요일 픽업 가능한가요?",
      "createdAt": "2026-08-21T15:00:00+09:00"
    },
    {
      "id": 30,
      "content": "아직 판매 중인가요?",
      "createdAt": "2026-08-21T14:30:00+09:00"
    }
  ]
}
```

쪽지가 없으면:

```
{
  "listingId": 1,
  "items": []
}
```

### 다른 회원의 매물 `403`

```
{
  "code": "LISTING_ACCESS_DENIED",
  "message": "해당 매물의 쪽지를 확인할 권한이 없습니다."
}
```

---

## 검증 규칙

| 필드 | 규칙 |
| --- | --- |
| `phoneNumber` | 필수, 숫자 정규화 후 10~11자리, 회원 간 유일 |
| `password` | 필수, 8~100자, BCrypt 해시 저장, 첫 로그인 시 이 값으로 가입 |
| `title` | 필수, 1~100자 |
| `description` | 필수, 1~500자 |
| `pickupTimeText` | 필수, 1~50자 |
| `canHelpMove` | 필수, `true` 또는 `false` |
| `images` | 필수, 최소 1장, 최대 5장 |
| 이미지 형식 | JPEG, PNG, WebP |
| 이미지 용량 | 전체 요청 최대 25MB |
| `message.content` | 필수, 1~500자 |

`phoneNumber`는 공백과 하이픈을 제거한 뒤 검증하고 저장합니다. 응답에도 정규화된 값을 돌려줍니다.

## 공통 오류 응답

```
{
  "code": "INVALID_REQUEST",
  "message": "요청 값이 올바르지 않습니다."
}
```

| HTTP | Code | 조건 |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | 필수값 누락 또는 형식 오류, 수정 요청에 변경할 값이 없음 |
| `400` | `INVALID_IMAGE_COUNT` | 이미지가 1장 미만 또는 5장 초과 |
| `401` | `INVALID_CREDENTIALS` | 로그인 실패 |
| `401` | `AUTHENTICATION_REQUIRED` | 로그인 세션 없음 |
| `403` | `LISTING_ACCESS_DENIED` | 다른 회원의 매물 접근 |
| `404` | `LISTING_NOT_FOUND` | 매물이 존재하지 않음 |
| `413` | `PAYLOAD_TOO_LARGE` | 이미지 전체 용량 25MB 초과 |
| `415` | `UNSUPPORTED_IMAGE_TYPE` | 지원하지 않는 이미지 형식 |
| `500` | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

이 오류 형식은 프로토타입 API에만 적용합니다. 견적·배차 API의 기존 오류 형식은 그대로 둡니다.

## 아직 정하지 않은 것

- 개인정보 보관·삭제 기간과 동의 증적(DEC-020 미정)
- 휴대폰 번호 실제 소유 확인, 비밀번호 재설정. 지금은 번호 소유를 확인하지 않으므로 그 번호로 먼저 로그인한 사람이 계정을 갖습니다. 실제 사용자 공개 전에 결정이 필요합니다.
- 쪽지 답장·알림, 매물 판매 상태, 검색·정렬·페이지네이션
- 프로토타입 프론트 연동에 필요한 CORS 설정(현재 dev CORS는 `PATCH`·`DELETE`와 인증정보 전송을 허용하지 않음)
