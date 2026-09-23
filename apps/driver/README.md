# SETTY 배송원 앱 (apps/driver)

배송원(기사)이 배차 요청을 받고 수락·수령·배송완료까지 처리하는 Expo(React Native) 앱.
배송 팀 단말이 모두 iPhone이라 **iOS 우선**으로 확인한다.

문서는 `apps/docs`를 본다: 아키텍처·폴더 규칙(`architecture.md`), API 매핑(`api-mapping.md`), 미정 사항(`open-questions.md`).

## 실행

```bash
cd apps/driver
npm install
# 설치한 Expo SDK에 맞춰 의존성 버전을 정렬(권장)
npx expo install --fix

npx expo start
```

- iPhone에 **Expo Go** 앱을 설치하고, 터미널의 QR을 카메라로 스캔한다(PC와 폰이 같은 Wi-Fi).
- 베이스 URL을 비워 두면 목 데이터로 뜬다. 실제 서버에 붙이려면 `.env`에
  `EXPO_PUBLIC_API_BASE_URL`을 채운다(`.env.example` 참고). 실기기에서는 `localhost`가
  아니라 PC의 LAN IP를 써야 한다.

## 이번 범위 (Issue #173)

- 하단 탭: 요청 / 내 배차 / 정산
- 요청: 목록 → 수신 상세 → 수락(수락하면 내 배차로 이동). 거절은 로컬에서만 숨김(서버 미반영).
- 내 배차: 진행중 / 완료 목록 → 상세 → 수령 → 배송완료(상태 전이, 409 가드)
- 정산: 완료 배차에서 앱이 계산(오늘 수입·완료 건수·건당 평균)

후속: 로그인·회원가입, UUID 헤더 인증, 거절 API.
