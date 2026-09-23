/**
 * 세션 동안 '로컬 거절'한 요청 id 집합.
 *
 * 거절 API가 없어서 서버에는 반영하지 않는다(요청은 서버에서 여전히 REQUESTED).
 * 대신 이 집합으로 목록에서 숨겨, 홈을 다시 조회해도 거절한 요청이 되살아나지
 * 않게 한다. 앱을 완전히 종료하면 초기화된다. (미정 사항 — apps/docs 참고)
 */
const rejected = new Set<number>();

export const rejectedStore = {
  add(deliveryId: number): void {
    rejected.add(deliveryId);
  },
  has(deliveryId: number): boolean {
    return rejected.has(deliveryId);
  },
  /** 거절한 항목을 걷어낸 목록을 돌려준다. */
  filter<T extends { deliveryId: number }>(list: T[]): T[] {
    return list.filter((x) => !rejected.has(x.deliveryId));
  },
  clear(): void {
    rejected.clear();
  },
};
