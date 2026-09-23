/**
 * 디자인(배차앱.dc.html)에서 그대로 옮긴 색 토큰이다.
 * 값은 4/8px 그리드나 프레임워크 기본값으로 반올림하지 않고 원본을 유지한다.
 */
export const colors = {
  // 배경
  screenBg: '#F4FCFA',
  gradientTop: '#C9FDF2',
  gradientMid: '#B3EBF2',
  gradientBottom: '#8FD9E4',

  // 잉크 / 텍스트
  ink: '#0C3138', // 가장 진한 제목
  brand: '#123C43', // 주 버튼 · 강조
  brandPressed: '#0C2E34',
  teal: '#0E5D66',
  tealDeep: '#0E4A63',

  textMuted: '#5B7B80',
  textFaint: '#8AA6AA',
  textFaint2: '#93AEB1',
  textOnDarkSub: '#9DE0E8',

  // 카드 · 경계
  cardBg: '#FFFFFF',
  cardBgDone: '#F1F8F6',
  cardBorder: '#EAF4F2',
  divider: '#E4F0EE',
  dividerSoft: '#DDF3EF',

  // 액센트(경로 · 점)
  accent: '#85D1DB',
  accent2: '#5FC6D6',
  accentSoft: '#D6F4F8',

  // pill 배경/글자
  pillNeutralBg: '#EAF9F7',
  pillNeutralText: '#3C6A70',
  pillGreenBg: '#EDFAF1',
  pillGreenText: '#2C7A63',

  // 입력
  inputBorder: '#DCEAE8',
  inputFocus: '#123C43',

  // 상태 강조
  liveDot: '#38C48A',
  danger: '#F26D6D',
  dangerText: '#D14343',
  rejectBgFrom: '#FDE7E7',
  rejectBgTo: '#FBD3D3',

  // 하단 탭
  navActive: '#123C43',
  navInactive: '#9BB6B9',
  navSurface: 'rgba(255,255,255,0.92)',

  white: '#FFFFFF',
} as const;

export type ColorToken = keyof typeof colors;
