import { ComponentProps } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Feather } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { colors } from '@/theme';
import { AppText } from './AppText';

type FeatherName = ComponentProps<typeof Feather>['name'];

/** 라우트 이름 → 탭 아이콘. */
const ICONS: Record<string, FeatherName> = {
  index: 'home',
  shipments: 'list',
  settlement: 'dollar-sign',
};

/** 디자인의 하단 네비게이션바(요청/내 배차/정산). */
export function BottomTabBar({ state, descriptors, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.bar, { paddingBottom: insets.bottom + 10 }]}>
      {state.routes.map((route, index) => {
        const focused = state.index === index;
        const { options } = descriptors[route.key];
        const label = options.title ?? route.name;
        const color = focused ? colors.navActive : colors.navInactive;
        const icon = ICONS[route.name] ?? 'circle';

        const onPress = () => {
          const event = navigation.emit({
            type: 'tabPress',
            target: route.key,
            canPreventDefault: true,
          });
          if (!focused && !event.defaultPrevented) {
            navigation.navigate(route.name as never);
          }
        };

        return (
          <Pressable key={route.key} onPress={onPress} style={styles.item}>
            <Feather name={icon} size={24} color={color} />
            <AppText variant="bold" style={[styles.label, { color }]}>
              {label}
            </AppText>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    backgroundColor: colors.navSurface,
    borderTopWidth: 1,
    borderTopColor: colors.divider,
    paddingTop: 10,
    paddingHorizontal: 30,
  },
  item: { flex: 1, alignItems: 'center', gap: 4 },
  label: { fontSize: 11 },
});
