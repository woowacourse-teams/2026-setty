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
| `SETTY_ENABLE_DEMO_FILL` | `true`면 회원가입 화면에 전화번호·행정동·주소를 시연 값으로 채우는 버튼을 보여준다. 부스 시연용. |

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

## 배송원 웹

배송원 계정으로 로그인해 다음 경로를 사용한다.

| 경로 | 화면 |
| :-- | :-- |
| `/driver` | 배차 요청 목록 |
| `/driver/requests/:deliveryId` | 배차 요청 상세 |
| `/driver/shipments` | 진행 중과 배송 완료 목록 |
| `/driver/shipments/:deliveryId` | 내 배차 상세 |
| `/driver/me` | 내 정보와 전체 배송 완료 수입 |

목록과 상세는 매물의 첫 번째 사진을 표시한다. 사진이 없거나 로딩에 실패하면 가구 종류를 표시한다.

배차 요청과 내 배차의 목록 및 상세는 판매자 수령지에서 구매자 목적지까지의 예상 거리를 km로 표시한다. 서버의 `estimatedDistanceMeters`는 배송에 저장된 두 행정동 코드로 기존 서울 도로 거리표를 조회한 값이다. 행정동 대표점 기준 추정치이며 기사 현재 위치나 건물 사이의 실제 주행 거리가 아니다. 코드가 없거나 서울 거리표 범위 밖이면 `null`을 받아 `거리 정보 없음`으로 표시한다. 거리표의 산출 기준은 [배송비 거리표 문서](../server/scripts/deliveryfee/README.md)를 참고한다.

목록은 `size=20`을 보내 `{ items, nextCursor }`를 받고 하단에서 다음 페이지를 불러온다. 배차 요청은 요청 시각순이며 내 배차는 수락 시각순이다. 두 목록 모두 같은 시각이면 ID 내림차순으로 정렬한다. 내 배차는 `group=IN_PROGRESS`와 `group=DELIVERED`를 각각 조회한다. API의 최대 페이지 크기는 100건이다.

배차 요청의 SSE 수신과 연결 복구 및 대체 폴링은 이전 커서 경계를 포함하는 페이지까지 다시 조회한다. 경계 항목이 수락되어 사라져도 시각과 ID 경계를 지나면 멈추며 끝까지 읽었던 목록은 마지막 페이지까지 갱신한다. 갱신이 끝날 때 목록과 커서를 함께 바꾸며 상세에서 돌아와도 기존 목록을 유지한다. 추가 로딩 실패 시 기존 항목을 유지하고 다시 불러오기 버튼을 제공한다.

`/driver/me`의 합계는 페이지 일부로 계산하지 않는다. `cursor`, `size`, `group`을 생략한 기존 내 배차 전체 조회를 사용한다. 이 응답은 사진과 거리를 조회하지 않고 `thumbnailUrl`과 `estimatedDistanceMeters`를 `null`로 제공한다. 페이지 조회와 상세 조회는 대표 사진과 예상 거리를 제공한다. 조회 계약은 [배송 컨트롤러](../server/src/main/java/setty/delivery/api/DeliveryController.java), 페이지 상태는 [deliveryPageStore.ts](src/features/driver/deliveryPageStore.ts)를 참고한다.

검증은 `npm test`, `npm run type-check`, `npm run build`로 실행한다.

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

[로컬 AI 상담 실행·데스크톱 검증 안내](../server/scripts/README-local-support.md)
