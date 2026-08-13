/**
 * 구매자가 판매자에게 보내는 안내문이다.
 * 구매자가 서비스를 직접 설명하지 않아도 되도록 링크와 함께 한 번에 전달한다.
 */
export function buildSellerGuideMessage(sellerInputUrl: string): string {
  return [
    '안녕하세요! 중고거래 물품 배송을 위해 세티(SETTY) 라는 배송 대행 서비스를 이용하려고 해요.',
    '아래 링크에서 발송지 주소랑 가능 시간만 입력해주시면 돼요 (1분 소요).',
    '입력하신 연락처·주소는 저에게 공개되지 않고 배송 기사님 배정에만 사용돼요.',
    sellerInputUrl,
  ].join('\n');
}
