import { Text, TextProps, TextStyle } from 'react-native';
import { fonts } from '@/theme';

type Variant = 'display' | 'regular' | 'medium' | 'bold' | 'black';

const FAMILY: Record<Variant, string> = {
  display: fonts.display,
  regular: fonts.regular,
  medium: fonts.medium,
  bold: fonts.bold,
  black: fonts.black,
};

interface AppTextProps extends TextProps {
  /** 기본 regular. 제목·금액은 display(Do Hyeon). */
  variant?: Variant;
}

/**
 * 폰트 패밀리를 한 곳에서 강제하는 텍스트. 컴포넌트는 색/크기만 style로 넘긴다.
 * fontFamily를 직접 쓰지 말고 이 컴포넌트를 통해 렌더한다.
 */
export function AppText({ variant = 'regular', style, ...rest }: AppTextProps) {
  return <Text {...rest} style={[{ fontFamily: FAMILY[variant] } as TextStyle, style]} />;
}
