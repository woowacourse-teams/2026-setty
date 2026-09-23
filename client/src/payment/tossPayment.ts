// 토스페이먼츠 결제위젯 테스트 클라이언트 키(공개 docs 키). 실제 결제는 붙이지 않는다.
// 서버는 짝이 되는 위젯 시크릿 키(test_gsk_docs_Ovk5rk1…)로 승인을 호출한다. 목 모드에서는 사용되지 않는다.
export const TOSS_CLIENT_KEY = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';

/**
 * 토스 결제창이 성공/실패 시 돌아올 서버 복귀 엔드포인트.
 * 서버(GET /payments/success·/fail)가 결제를 승인·기록하고 PaymentCompleted/Failed를 발행한 뒤,
 * PAYMENT_CLIENT_REDIRECT_URL(SPA)로 ?payment=success|fail 을 붙여 다시 302한다.
 *
 * 주의: /payments/** 는 SPA가 아니라 백엔드로 라우팅돼야 한다(배포 리버스 프록시 설정).
 */
export function paymentReturnUrl(result: 'success' | 'fail'): string {
    return `${window.location.origin}/payments/${result}`;
}

/** 목 모드: 서버 없이 결제 성공 복귀(SPA ?payment=success)를 흉내낸다. */
export function mockReturn(orderId: number) {
    const url = new URL(window.location.origin);
    url.searchParams.set('payment', 'success');
    url.searchParams.set('orderId', String(orderId));
    window.location.assign(url.toString());
}
