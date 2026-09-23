/**
 * 회원가입 입력 마스크. 사용자가 숫자만 입력해도 하이픈을 자동으로 넣는다.
 * 입력 중(부분 입력)에도 자연스럽게 포맷되며, 결과는 서버 정규식과 맞는다.
 */

/** 010-0000-0000 (숫자 11자 → 3-4-4). */
export function formatPhoneNumber(input: string): string {
  const d = input.replace(/\D/g, '').slice(0, 11);
  if (d.length <= 3) return d;
  if (d.length <= 7) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`;
}

/** 000-00-00000 (숫자 10자 → 3-2-5). */
export function formatBusinessNumber(input: string): string {
  const d = input.replace(/\D/g, '').slice(0, 10);
  if (d.length <= 3) return d;
  if (d.length <= 5) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 5)}-${d.slice(5)}`;
}
