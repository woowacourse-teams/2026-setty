/**
 * 여백 토큰. 디자인에서 반복되는 값만 의미 있는 이름으로 노출한다.
 * 라인 하나에만 쓰는 특수 값은 각 컴포넌트 스타일에서 직접 숫자로 둔다.
 */
export const spacing = {
  screenX: 22, // 화면 좌우 기본 패딩
  cardPad: 16,
  gapXs: 4,
  gapSm: 8,
  gapMd: 12,
  gapLg: 16,
  gapXl: 20,
} as const;

export type SpacingToken = keyof typeof spacing;
