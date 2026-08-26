import { Instant } from '@/model/delivery';

/** 배송비 등 원화 표기: 35000 → "35,000원" */
export function formatFee(won: number): string {
  return `${won.toLocaleString('ko-KR')}원`;
}

/**
 * 주소에서 마지막 토큰(동 단위)만 보여준다.
 * "서울 성동구 성수동" → "성수동" (개인정보 최소 노출 · 카드 요약용)
 */
export function shortAddress(address: string): string {
  const parts = (address ?? '').trim().split(/\s+/);
  return parts[parts.length - 1] ?? '';
}

/** 픽업 → 도착 요약 경로 문자열 */
export function routeText(pickup: string, destination: string): string {
  return `${shortAddress(pickup)} → ${shortAddress(destination)}`;
}

/**
 * Instant(ISO 문자열)를 한국시(KST) HH:mm 으로. 값이 없으면 빈 문자열.
 * server가 이미 오프셋을 포함하므로 Date 파싱 후 Asia/Seoul 기준으로 표기한다.
 */
export function formatTimeKst(iso: Instant | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'Asia/Seoul',
  }).format(d);
}
