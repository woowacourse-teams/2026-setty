/** 모서리 반경 토큰. */
export const radius = {
  chip: 20, // pill
  sm: 11,
  md: 13,
  lg: 16,
  xl: 18,
  card: 20,
  xxl: 22,
} as const;

export type RadiusToken = keyof typeof radius;
