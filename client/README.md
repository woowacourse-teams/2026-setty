# SETTY 웹 (client)

Android 앱(`android/`)의 구매자·판매자 화면을 모바일 브라우저에서 쓸 수 있게 옮긴 웹이다.
화면 구성·문구·검증 규칙·API 호출 흐름은 Android 앱을 따른다.

## 실행

```bash
cd client
npm install

# 로컬 서버(기본 http://localhost:8080)에 붙이기
npm run dev
SETTY_API_URL=https://dev.example.com npm run dev
```

개발 서버는 `http://localhost:3000`에서 뜨고 `/api` 요청을 `SETTY_API_URL`로 넘긴다.
같은 Wi-Fi의 휴대폰에서는 `http://<PC의 LAN IP>:3000`로 열 수 있다.

| 변수 | 설명 |
| :-- | :-- |
| `SETTY_API_URL` | 개발 서버가 `/api`를 넘길 백엔드 주소. |
| `SETTY_TOSS_CLIENT_KEY` | 토스페이먼츠 결제위젯 클라이언트 키. 비우면 공개 문서용 테스트 키를 쓴다. |

## 화면

| 경로 | Android 화면 |
| :-- | :-- |
| `/login`, `/signup` | 로그인, 회원가입 |
| `/` | 홈(카테고리·상태등급·물건 가격·배송비 예산 필터, 검색) |
| `/listings/:id` | 매물 상세(찜, 결제하고 주문하기) |
| `/payment` | 결제(토스 결제위젯) |
| `/me` | 내 정보 |
| `/me/profile` | 정보 수정(행정동 선택) |
| `/me/listings`, `/me/listings/new`, `/me/listings/:id/edit` | 내 판매 가구, 가구 등록·수정 |
| `/me/favorites` | 찜한 가구 |
| `/me/orders`, `/me/orders/:id` | 주문 내역, 주문 상세(주문 취소) |

키워드 알림·알림함·푸시 알림은 아직 옮기지 않았다.

## 결제 흐름

Android 앱과 같이 앱이 직접 서버 승인 API를 부른다.

1. 결제 화면에서 `POST /api/orders`로 PENDING 주문을 만들고 `<주문id>_<UUID>`를 토스 orderId로 쓴다.
2. 토스 결제위젯이 결제창을 열고, 끝나면 `/payment/success` 또는 `/payment/fail`로 돌아온다.
3. 성공이면 `POST /api/payments/confirm`(네트워크 에러면 최대 3번)을, 실패면 `POST /api/payments/fail`을 부른다.
4. 승인되면 매물 상세로 돌아가 "결제가 완료되었습니다" 안내와 주문 내역 바로가기를 보여준다.

## 배포

[`buildspec.yml`](buildspec.yml)이 `npm run build`로 만든 `dist/`를 S3에 올리고 CloudFront 캐시를 무효화한다.

- API를 같은 도메인의 `/api`로 부른다. CloudFront가 `/api/*`를 서버로 넘겨야 한다.
- `/me/orders`, `/payment/success` 같은 경로는 S3에 파일이 없다. CloudFront가 403·404를 `/index.html`(200)로 돌려줘야 새로고침과 결제 복귀가 동작한다.
- `/payment/success`, `/payment/fail`은 SPA 경로다. 서버 복귀 경로(`/payments/**`)와 다르다.
