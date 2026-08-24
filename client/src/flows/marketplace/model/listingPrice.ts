/** 서버 가격이 응답에 없거나 값이 유효하지 않을 때 화면에 대신 노출하는 문구다. */
export const PRICE_UNAVAILABLE_TEXT = '표시불가';

/**
 * 매물 가격을 화면 문구로 변환한다. 서버 DTO에 `price`가 아직 없거나 `null`이면
 * 값을 지어내지 않고 "표시불가"로 표시한다. 유효한 정수면 원 단위로 포맷한다.
 */
export function formatListingPrice(price?: number | null): string {
  if (price === null || price === undefined || !Number.isFinite(price)) {
    return PRICE_UNAVAILABLE_TEXT;
  }
  return `${Math.trunc(price).toLocaleString('ko-KR')}원`;
}
