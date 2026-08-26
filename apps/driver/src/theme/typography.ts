/**
 * 폰트 토큰.
 * - 디스플레이(제목·금액): Do Hyeon → `DoHyeon_400Regular`
 * - 본문: Noto Sans KR (400/500/700/900)
 * 실제 로딩은 app/_layout.tsx의 useFonts에서 한다. 여기서는 family 이름과
 * 자주 쓰는 텍스트 프리셋만 노출한다.
 */
export const fonts = {
  display: 'DoHyeon_400Regular',
  regular: 'NotoSansKR_400Regular',
  medium: 'NotoSansKR_500Medium',
  bold: 'NotoSansKR_700Bold',
  black: 'NotoSansKR_900Black',
} as const;

export type FontToken = keyof typeof fonts;

/** 폰트 로딩 실패 시에도 앱이 뜨도록 시스템 폴백을 함께 둔다. */
export const fallback = {
  sans: 'System',
} as const;
