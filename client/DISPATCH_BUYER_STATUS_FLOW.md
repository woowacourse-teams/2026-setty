# Issue #14 구매자 배차 상태 카드 구현 기준

- 기준 Issue: `#14 feat: 구매자가 판매자 정보 입력 여부를 실시간으로 확인하는 카드`
- 선행 계약: PR #32로 `GET /api/dispatch-requests/{buyerToken}`의 `sellerInputUrl` 병합 완료
- 소유 영역: `src/flows/dispatch/**`와 배차 route adapter

## 사용자 결과

```text
구매자가 배차 요청 생성
→ 판매자 입력 링크 전달
→ /dispatch/:buyerToken 카드에서 판매자 입력 여부 확인
→ 대기 중에는 동일 판매자 링크를 다시 복사
→ 판매자 입력 완료를 5초 폴링으로 감지
→ 운영자 배송 조건 검토 대기 표시
```

판매자 입력 완료를 문자로 자동 통지하지 않는다. 실제 최종 금액·조건 문자는 운영자가 외부에서 직접 발송한다.

## 직접 URL을 선택한 이유

- `buyerToken`을 React state에만 두면 새로고침·탭 종료 후 요청을 다시 열 수 없다.
- `sessionStorage`는 탭 종료 후 사라지므로 재방문 링크 역할을 하지 못한다.
- 계정이 없는 현재 MVP에서는 `buyerToken`이 구매자 조회용 capability token이다.
- 생성 화면의 공유·복사를 마치거나 `판매자 입력 상태 확인하기`를 선택하면 `/dispatch/:buyerToken`으로 `replace` 이동한다.
- 기존 Webpack `historyApiFallback`과 Vercel SPA rewrite가 직접 URL 새로고침을 처리하므로 배포 설정은 추가하지 않는다.

토큰을 로그·픽스처·스크린샷에 실제 값으로 남기지 않는다. 토큰 만료·재발급 정책은 DEC-023의 후속 결정이며 Issue #14에서 임의로 구현하지 않는다.

## API 연결

- 배차 API도 견적·운영자 API와 동일한 공용 `SETTY_API_BASE_URL`을 사용한다.
- 이 값은 Webpack 빌드 시 주입되므로 배포 환경에서 변경한 뒤에는 다시 빌드·배포한다.
- 미설정 시 개발 빌드는 `http://localhost:8080`, 운영 빌드는 같은 origin의 `/api/**`를 사용한다.

## 카드 표시 원칙

- 구매자에게 판매자의 이름·연락처·주소 등 입력 내용은 표시하지 않는다.
- `sellerInputCompleted=false` 동안만 판매자 링크와 복사 버튼을 표시한다.
- Clipboard API를 사용할 수 없거나 복사가 실패해도 읽기 전용 입력에서 링크를 직접 선택할 수 있다.
- `sellerInputCompleted=true`가 되면 폴링을 멈추고 링크 전달 UI를 숨긴다. 이미 제출된 링크를 다시 전달하라는 오해를 막기 위한 FE 표시 결정이다.
- 내부 상태 enum 대신 제품 문서의 사용자 표시 문구를 사용한다.

## 범위 밖

- 운영자 화면과 상태 변경
- 판매자 입력 완료 SMS
- WebSocket 등 push 통신
- 최종 금액 동의·거절
- 사용자 계정·요청 목록·토큰 재발급

## 검증 경계

- 생성 후 구매자 직접 URL 이동
- 직접 URL 새로고침·재방문
- 대기에서 완료로 5초 폴링 후 추가 조회 중단
- 링크 복사 성공·실패 fallback
- 알 수 없는 `buyerToken`의 404
- 판매자 개인정보 비노출
- 모바일 기본 레이아웃과 콘솔 오류
