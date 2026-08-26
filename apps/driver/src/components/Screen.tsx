import { ReactNode } from 'react';
import { StyleSheet, View, ViewStyle } from 'react-native';
import { SafeAreaView, Edge } from 'react-native-safe-area-context';
import { colors } from '@/theme';

interface ScreenProps {
  children: ReactNode;
  /** SafeArea를 적용할 가장자리. 기본 top/bottom. */
  edges?: Edge[];
  style?: ViewStyle;
  backgroundColor?: string;
}

/** 화면 공통 래퍼: 배경색 + SafeArea. */
export function Screen({
  children,
  edges = ['top', 'bottom'],
  style,
  backgroundColor = colors.screenBg,
}: ScreenProps) {
  return (
    <View style={[styles.root, { backgroundColor }]}>
      <SafeAreaView edges={edges} style={[styles.safe, style]}>
        {children}
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  safe: { flex: 1 },
});
