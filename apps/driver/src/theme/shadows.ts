import { Platform, ViewStyle } from 'react-native';

/**
 * 그림자 프리셋. 웹 CSS의 box-shadow를 RN(iOS shadow* / Android elevation)으로 옮긴다.
 * iOS 우선(배차 팀 단말이 모두 iPhone) — 값은 iOS 기준으로 맞추고 elevation은 근사치.
 */
function shadow(
  offsetY: number,
  radius: number,
  opacity: number,
  elevation: number,
): ViewStyle {
  return Platform.select<ViewStyle>({
    ios: {
      shadowColor: '#0C3138',
      shadowOffset: { width: 0, height: offsetY },
      shadowOpacity: opacity,
      shadowRadius: radius,
    },
    default: { elevation },
  })!;
}

export const shadows = {
  card: shadow(8, 12, 0.14, 3),
  cardRaised: shadow(10, 14, 0.2, 5),
  floating: shadow(14, 18, 0.35, 8),
} as const;

export type ShadowToken = keyof typeof shadows;
